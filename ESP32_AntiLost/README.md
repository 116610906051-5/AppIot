# ESP32 Anti-Lost Device
โค้ดสำหรับ ESP32 เพื่อใช้งานร่วมกับแอพ AntiLostApp

## 🔧 อุปกรณ์ที่ต้องใช้

1. **ESP32 Dev Board** (ชนิดใดก็ได้)
2. **LED** (ถ้าต้องการ - หรือใช้ LED บนบอร์ด GPIO 2)
3. **Buzzer ขนาด 5V** (ถ้าต้องการเสียงแจ้งเตือน)
4. **สายต่อ Micro USB**

## 📦 ติดตั้ง Arduino IDE

1. ดาวน์โหลด Arduino IDE จาก https://www.arduino.cc/en/software
2. เปิด Arduino IDE
3. ไปที่ **File > Preferences**
4. ใน "Additional Board Manager URLs" เพิ่ม:
   ```
   https://dl.espressif.com/dl/package_esp32_index.json
   ```
5. ไปที่ **Tools > Board > Boards Manager**
6. ค้นหา "esp32" และกด Install

## 📚 Library ที่ต้องติดตั้ง

ไลบรารีทั้งหมดมีมากับ ESP32 board package แล้ว:
- BLEDevice
- BLEUtils
- BLEServer
- BLE2902

## 🔌 การต่อวงจร

### แบบง่าย (ไม่ต้องต่ออะไรเพิ่ม)
```
ESP32 มี LED ในตัวที่ GPIO 2
```

### แบบเต็ม (มี Buzzer)
```
ESP32 GPIO 2  ──→ LED ──→ GND (หรือใช้ LED บนบอร์ด)
ESP32 GPIO 4  ──→ Buzzer (+)
ESP32 GND     ──→ Buzzer (-)
```

## 📝 การอัปโหลดโค้ด

1. เปิดไฟล์ `ESP32_AntiLost.ino` ใน Arduino IDE
2. เลือกบอร์ด: **Tools > Board > ESP32 Arduino > ESP32 Dev Module**
3. เลือก Port: **Tools > Port > COMx** (เลือก port ที่เชื่อมต่อ ESP32)
4. กดปุ่ม **Upload** (หรือ Ctrl+U)

## ⚙️ การตั้งค่า

### เปลี่ยนชื่ออุปกรณ์
ในไฟล์ `.ino` บรรทัดที่ 21:
```cpp
#define DEVICE_NAME "ESP32_AntiLost"
```
**หมายเหตุ:** ชื่อนี้ต้องตรงกับใน `MainActivity.kt` บรรทัดที่ 27

### เปลี่ยน Pin
```cpp
#define LED_PIN 2          // เปลี่ยนเป็น GPIO ที่ต่อ LED
#define BUZZER_PIN 4       // เปลี่ยนเป็น GPIO ที่ต่อ Buzzer
```

## 🚀 การใช้งาน

1. **อัปโหลดโค้ดลง ESP32**
2. **เปิด Serial Monitor** (Ctrl+Shift+M) เพื่อดูสถานะ
3. **LED จะกระพริบ** เมื่อพร้อมใช้งาน
4. **เปิดแอพ Android** และกดปุ่ม "เริ่มสแกน"
5. แอพจะเจออุปกรณ์ชื่อ "ESP32_AntiLost"
6. **LED จะติดค้าง** เมื่อเชื่อมต่อกับแอพสำเร็จ

## 🔔 ฟีเจอร์

### LED Indicator
- **กระพริบ 1 วินาที** = กำลังรอการเชื่อมต่อ (Advertising)
- **ติดค้าง** = เชื่อมต่อกับแอพสำเร็จ
- **กระพริบเร็ว 5 ครั้ง** = เริ่มต้นระบบ

### ปุ่ม BOOT
- กดปุ่ม BOOT บนบอร์ด ESP32 เพื่อทดสอบเสียง Beep

### BLE Advertising
- ESP32 จะส่งสัญญาณ Bluetooth ตลอดเวลา
- แอพ Android สามารถวัดความแรงสัญญาณ (RSSI)
- แจ้งเตือนเมื่อสัญญาณอ่อน (อยู่ไกลเกินไป)

## 🔍 การทดสอบ

1. เปิด Serial Monitor (115200 baud)
2. จะเห็นข้อความ:
   ```
   Starting ESP32 Anti-Lost Device...
   BLE Advertising started!
   Device Name: ESP32_AntiLost
   Waiting for Android app to scan...
   ```
3. เมื่อแอพเชื่อมต่อจะแสดง:
   ```
   Device Connected!
   ```

## 📱 การทำงานร่วมกับแอพ

### ระดับสัญญาณ (RSSI)
- **-40 ถึง -60 dBm** = ใกล้มาก (แรงมาก)
- **-60 ถึง -70 dBm** = ใกล้ปานกลาง (แรง)
- **-70 ถึง -80 dBm** = ไกลปานกลาง (ปานกลาง)
- **-80 ถึง -100 dBm** = ไกลมาก (อ่อน) ⚠️ แจ้งเตือน

### การแจ้งเตือน
เมื่อ RSSI < threshold ที่ตั้งไว้ แอพจะ:
- แสดงการ์ดสีแดง
- ส่งเสียงแจ้งเตือน
- แสดงข้อความ "⚠️ สัญญาณอ่อน! อาจอยู่ไกลเกินไป"

## 🐛 แก้ปัญหา

### ไม่สามารถอัปโหลดโค้ดได้
- กดปุ่ม BOOT บน ESP32 ค้างไว้ขณะกด Upload
- ตรวจสอบว่าเลือก Port และ Board ถูกต้อง
- ลองถอดปลั๊กและเสียบใหม่

### แอพหา ESP32 ไม่เจอ
- ตรวจสอบว่า ESP32 มีไฟติด
- เปิด Serial Monitor ดูว่ามีข้อความ "BLE Advertising started!"
- ตรวจสอบชื่ออุปกรณ์ว่าตรงกันใน ESP32 และแอพ
- ลอง restart ESP32 (กดปุ่ม EN)

### แอพเชื่อมต่อแล้วขาดบ่อย
- ลองลดระยะห่างระหว่างมือถือกับ ESP32
- ตรวจสอบแบตเตอรี่ของทั้งสองอุปกรณ์
- หลีกเลี่ยงสิ่งกีดขวางระหว่างอุปกรณ์

### LED ไม่กระพริบ
- ตรวจสอบการต่อวงจร LED
- ลองเปลี่ยน `LED_PIN` เป็น GPIO อื่น
- ESP32 บางรุ่นใช้ GPIO 5 แทน GPIO 2

## 💡 การพัฒนาต่อ

### เพิ่มฟีเจอร์
1. **Battery Monitor** - แสดงแบตเตอรี่คงเหลือ
2. **Temperature Sensor** - แจ้งเตือนเมื่ออุณหภูมิสูง
3. **Motion Sensor** - ตรวจจับการเคลื่อนไหว
4. **GPS Module** - บันทึกตำแหน่ง

### Custom Commands
แก้ไขใน `MyCallbacks::onWrite()` เพื่อรับคำสั่งจากแอพ:
```cpp
if (value == "YOUR_COMMAND") {
    // ทำอะไรสักอย่าง
}
```

## 📄 License
MIT License - ใช้ได้ฟรี แก้ไขได้ตามใจชอบ

## 🤝 ติดต่อ
หากมีปัญหาหรือข้อสงสัย สามารถเปิด Issue ได้เลย
