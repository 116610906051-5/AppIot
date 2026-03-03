/*
 * ESP32 Anti-Lost Device
 * ใช้งานร่วมกับ AntiLostApp
 * 
 * ฟีเจอร์:
 * - BLE Advertising เพื่อให้แอพสแกนเจอ
 * - LED แสดงสถานะ
 * - Buzzer แจ้งเตือน (ถ้ามี)
 * 
 * การต่อวงจร:
 * - LED: GPIO 2 (LED บนบอร์ด)
 * - Buzzer (ถ้ามี): GPIO 4
 * - Button (ถ้ามี): GPIO 0 (BOOT button)
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <esp_bt.h>

// ตั้งค่า Pin
#define LED_PIN 2          // LED บนบอร์ด ESP32
#define BUZZER_PIN 4       // Buzzer (ถ้ามี)
#define BUTTON_PIN 0       // ปุ่ม BOOT

// ชื่ออุปกรณ์ (ต้องตรงกับใน MainActivity.kt)
#define DEVICE_NAME "ESP32_AntiLost"

// UUID สำหรับ BLE Service และ Characteristic
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

unsigned long lastBlinkTime = 0;
bool ledState = false;

// Forward declarations
void beep();
void startupBlink();

// สถานะการเชื่อมต่อ
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Device Connected!");
      digitalWrite(LED_PIN, HIGH); // LED ติดค้างเมื่อเชื่อมต่อ
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Device Disconnected!");
    }
};

// Callback สำหรับรับข้อมูลจากแอพ
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      uint8_t* data = pCharacteristic->getData();
      size_t len = pCharacteristic->getValue().length();
      String value = "";
      
      for (int i = 0; i < len; i++) {
        value += (char)data[i];
      }

      if (value.length() > 0) {
        Serial.print("Received: ");
        Serial.println(value);

        // ถ้าได้รับคำสั่ง "BEEP" ให้ส่งเสียง
        if (value == "BEEP") {
          beep();
        }
        // ถ้าได้รับคำสั่ง "LED_ON"
        else if (value == "LED_ON") {
          digitalWrite(LED_PIN, HIGH);
        }
        // ถ้าได้รับคำสั่ง "LED_OFF"
        else if (value == "LED_OFF") {
          digitalWrite(LED_PIN, LOW);
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Starting ESP32 Anti-Lost Device...");

  // ตั้งค่า Pin
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // แสดงสัญญาณเริ่มต้น
  startupBlink();

  // สร้าง BLE Device
  BLEDevice::init(DEVICE_NAME);
  
  // สร้าง BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // สร้าง BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // สร้าง BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );

  // เพิ่ม Descriptor
  pCharacteristic->addDescriptor(new BLE2902());
  pCharacteristic->setCallbacks(new MyCallbacks());

  // เริ่ม Service
  pService->start();

  // เพิ่มกำลังส่งสัญญาณให้สูงสุด (ลดความผันผวน)
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, ESP_PWR_LVL_P9);
  esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_DEFAULT, ESP_PWR_LVL_P9);

  // เริ่ม Advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  
  // ตั้งค่า advertising interval ให้สม่ำเสมอ (ลดความผันผวน)
  // ค่า: 0x20 = 32 * 0.625ms = 20ms, 0x40 = 64 * 0.625ms = 40ms
  pAdvertising->setMinInterval(0x20);  // 20ms
  pAdvertising->setMaxInterval(0x40);  // 40ms
  
  BLEDevice::startAdvertising();
  
  Serial.println("BLE Advertising started!");
  Serial.println("TX Power: Maximum (+9 dBm)");
  Serial.println("Advertising Interval: 20-40ms");
  Serial.println("Device Name: " + String(DEVICE_NAME));
  Serial.println("Waiting for Android app to scan...");
}

void loop() {
  // กระพริบ LED เมื่อไม่ได้เชื่อมต่อ
  if (!deviceConnected) {
    unsigned long currentTime = millis();
    if (currentTime - lastBlinkTime >= 1000) {
      lastBlinkTime = currentTime;
      ledState = !ledState;
      digitalWrite(LED_PIN, ledState);
    }
  }

  // ตรวจสอบปุ่ม (กด BOOT button เพื่อ beep)
  if (digitalRead(BUTTON_PIN) == LOW) {
    delay(50); // debounce
    if (digitalRead(BUTTON_PIN) == LOW) {
      Serial.println("Button pressed - Beeping!");
      beep();
      while(digitalRead(BUTTON_PIN) == LOW); // รอปล่อยปุ่ม
    }
  }

  // จัดการการเชื่อมต่อใหม่
  if (!deviceConnected && oldDeviceConnected) {
    delay(500); // รอให้ Bluetooth stack พร้อม
    pServer->startAdvertising(); // เริ่ม advertising ใหม่
    Serial.println("Start advertising again...");
    oldDeviceConnected = deviceConnected;
  }
  
  // เชื่อมต่อใหม่
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  delay(10);
}

// ฟังก์ชันเสียง Beep
void beep() {
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
}

// ฟังก์ชันกระพริบเมื่อเริ่มต้น
void startupBlink() {
  for (int i = 0; i < 5; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
}
