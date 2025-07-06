# 🦯 Asistente de Navegación para Personas con Discapacidad Visual

Una aplicación Android diseñada para ayudar a personas ciegas o con baja visión a desplazarse de forma segura por la ciudad, mediante el uso de tecnologías BLE, visión artificial, geolocalización y traducción automática.

---

## 📲 Funcionalidades principales

- 🔗 **Conexión BLE** con un dispositivo ESP32 que transmite:
  - Coordenadas GPS (latitud, longitud)
  - Descripciones de objetos detectados por cámara usando **Google Vision AI**
  
- 🗣️ **Descripción hablada** de objetos frente al usuario, usando:
  - Traducción automática (inglés → español) con **ML Kit Translate**
  - Reproducción por voz con **TextToSpeech**

- 🗺️ **Identificación de ubicación actual**
  - Determina la calle y colonia del usuario mediante la API de geocodificación inversa de **Nominatim (OpenStreetMap)**

- 🚨 **Botón de emergencia**
  - Envía un **SMS de auxilio** con ubicación al contacto previamente configurado

---

## 🛠️ Tecnologías utilizadas

- **Lenguaje:** Kotlin  
- **Arquitectura:** MVVM + Clean Architecture  
- **UI:** Jetpack Compose + Material 3  
- **Conectividad:** Bluetooth Low Energy (BLE)  
- **Red:** Retrofit  
- **Geolocalización:** Nominatim API  
- **AI/Vision:** Google Vision AI (API externa vía ESP32)  
- **Traducción automática:** ML Kit Translate  
- **Texto a voz:** Android TextToSpeech  
- **DI:** Hilt  
- **Gestión de estado:** StateFlow  

---
