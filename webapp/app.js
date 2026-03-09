import { initializeApp } from "https://www.gstatic.com/firebasejs/11.4.0/firebase-app.js";
import {
  getFirestore,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyATVBbdOc17x9V8TH0-iaxQ0bZ9ZauLbUs",
  authDomain: "gpslog-9b772.firebaseapp.com",
  projectId: "gpslog-9b772",
  storageBucket: "gpslog-9b772.firebasestorage.app",
  messagingSenderId: "22715756500",
  appId: "1:22715756500:web:antilostweb001"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

const logList = document.getElementById("logList");
const refreshBtn = document.getElementById("refreshBtn");
const latestStatus = document.getElementById("latestStatus");
const connectionState = document.getElementById("connectionState");
const syncMessage = document.getElementById("syncMessage");
const selectedInfo = document.getElementById("selectedInfo");

let unsubscribeLogs = null;
let selectedCard = null;
let map = null;
let marker = null;

function initMap() {
  if (!window.L) {
    selectedInfo.textContent = "โหลดแผนที่ไม่สำเร็จ";
    return;
  }

  map = window.L.map("mapCanvas", {
    zoomControl: true,
    attributionControl: true
  }).setView([13.7563, 100.5018], 11);

  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap"
  }).addTo(map);

  marker = window.L.marker([13.7563, 100.5018]).addTo(map);
  marker.bindPopup("รอเลือกตำแหน่งจากรายการ log");
}

function focusMap(log) {
  if (!map || !marker) {
    return;
  }

  const lat = Number(log.latitude ?? 0);
  const lng = Number(log.longitude ?? 0);

  map.flyTo([lat, lng], 16, { duration: 0.8 });
  marker.setLatLng([lat, lng]);
  marker.bindPopup(`${log.deviceName || "Unknown Device"}<br>${log.status || "Unknown"}`).openPopup();
  selectedInfo.textContent = `${log.deviceName || "Unknown"} • ${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}

function formatTime(timestamp) {
  if (!timestamp) {
    return "Unknown";
  }

  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  return date.toLocaleString("th-TH", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
}

function renderLogs(items) {
  logList.innerHTML = "";
  selectedCard = null;

  if (!items.length) {
    const empty = document.createElement("article");
    empty.className = "log-card";
    empty.innerHTML = "<div class=\"log-meta\">ยังไม่มีข้อมูล LocationLogs</div>";
    logList.appendChild(empty);
    latestStatus.textContent = "No Data";
    return;
  }

  latestStatus.textContent = items[0].status || "Unknown";

  items.forEach((log) => {
    const card = document.createElement("article");
    card.className = "log-card";

    const badgeClass = (log.status || "Disconnected").toLowerCase();
    card.innerHTML = `
      <div class="log-header">
        <strong>${log.deviceName || "Unknown Device"}</strong>
        <span class="badge ${badgeClass}">${log.status || "Unknown"}</span>
      </div>
      <div class="log-meta">${log.time} • ${log.latitude.toFixed(6)}, ${log.longitude.toFixed(6)}</div>
      <div class="log-actions">
        <button class="map-btn show-on-map">แสดงบนแผนที่</button>
        <button class="map-btn outline open-new-tab">เปิดแผนที่แบบเต็ม</button>
      </div>
    `;

    card.querySelector(".show-on-map").addEventListener("click", () => {
      if (selectedCard) {
        selectedCard.classList.remove("active");
      }
      card.classList.add("active");
      selectedCard = card;
      focusMap(log);
    });

    card.querySelector(".open-new-tab").addEventListener("click", () => {
      const mapsUrl = `https://maps.google.com/?q=${log.latitude},${log.longitude}`;
      window.open(mapsUrl, "_blank", "noopener,noreferrer");
    });

    logList.appendChild(card);
  });

  focusMap(items[0]);
}

function subscribeLogs() {
  if (unsubscribeLogs) {
    unsubscribeLogs();
  }

  syncMessage.textContent = "กำลัง sync กับ Firestore...";

  const q = query(
    collection(db, "LocationLogs"),
    orderBy("timestamp", "desc"),
    limit(100)
  );

  unsubscribeLogs = onSnapshot(
    q,
    (snapshot) => {
      const logs = snapshot.docs.map((doc) => {
        const data = doc.data();
        return {
          status: data.status,
          deviceName: data.deviceName,
          latitude: Number(data.latitude ?? 0),
          longitude: Number(data.longitude ?? 0),
          time: formatTime(data.timestamp)
        };
      });

      connectionState.textContent = "เชื่อมต่อ Firestore แล้ว";
      syncMessage.textContent = `อัปเดตล่าสุด: ${new Date().toLocaleTimeString("th-TH")}`;
      renderLogs(logs);
    },
    (error) => {
      connectionState.textContent = "เชื่อมต่อล้มเหลว";
      latestStatus.textContent = "Error";
      syncMessage.textContent = `เกิดข้อผิดพลาด: ${error.message}`;
      renderLogs([]);
    }
  );
}

refreshBtn.addEventListener("click", () => {
  refreshBtn.textContent = "Refreshing...";
  subscribeLogs();
  setTimeout(() => {
    refreshBtn.textContent = "Refresh";
  }, 500);
});

initMap();
subscribeLogs();
