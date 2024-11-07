# DoorUnlock
ESP8266 Door Unlock System with Web Update and Static IP Configuration. A local door unlock system based on esp8266's local web server hosting capabilities 

This project is a basic web server application that allows remote control of a door-lock relay using an ESP8266 and a 4-channel relay module. This setup provides control over the relay through a web interface, with the ESP8266 connected to WiFi and accessible via a static IP. You can update the firmware remotely through HTTP.

---

## Hardware
- Esp32
- Relay Module 
- 5V Dc Power Supply

## Overview

The ESP8266 connects to a local WiFi network with a pre-set SSID and password. Once connected, it:
- Hosts a web server with endpoints for testing connectivity and unlocking the door relay.
- Configures a static IP for consistent network access.
- Supports OTA (Over-The-Air) firmware updates through a browser.

## Prerequisites
- An ESP8266 microcontroller (ESP-01, NodeMCU, or any other variant)
- 4-channel relay module, with one channel used for door lock control
- A stable WiFi network

### Required Libraries
Ensure you have the following libraries installed:
- `ESP8266WiFi.h`: Provides WiFi functions for ESP8266.
- `ESP8266WebServer.h`: Sets up a basic web server.
- `ESP8266HTTPUpdateServer.h`: Allows HTTP-based OTA updates.
- `ESP8266mDNS.h`: Enables mDNS (for local network access using a name instead of IP).

## Code Explanation

### Define WiFi Credentials and Static IP
```cpp
#define STASSID "xyz"
#define STAPSK "xyz"
```
Replace `"xyz"` with your WiFi SSID and password. These will be used to connect the ESP8266 to your network.

### Static IP Configuration
The static IP setup ensures the ESP8266 maintains the same IP address each time it connects:
```cpp
IPAddress staticIP(10, 0, 0, 55); // ESP8266 static IP
IPAddress gateway(10, 0, 0, 1);   // WiFi Router (Gateway) IP
IPAddress subnet(255, 255, 255, 0);  // Subnet mask
IPAddress dns(10, 0, 0, 1);  // DNS IP
```
Adjust these settings to match your network configuration.

### Setup

```cpp
void setup() {
  pinMode(5, OUTPUT);         // Set GPIO5 (Relay Control Pin) as output
  digitalWrite(5, HIGH);      // Initialize relay state to off (HIGH)
  Serial.begin(115200);       // Start serial communication
  WiFi.mode(WIFI_AP_STA);     // Set WiFi to both AP and Station mode
  WiFi.hostname(host);        // Set hostname for local access
  WiFi.begin(ssid, password); // Start WiFi connection

  while (WiFi.waitForConnectResult() != WL_CONNECTED) {
    WiFi.begin(ssid, password);
    Serial.println("WiFi failed, retrying.");
  }
  
  MDNS.begin(host);           // Start mDNS for hostname access
  httpUpdater.setup(&httpServer);
  httpServer.on("/", handleRootPath);
  httpServer.on("/unlock", handleUnlockPath);
  httpServer.begin();

  Serial.printf("HTTPUpdateServer ready! Open http://%s.local/update in your browser\n", host);
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
  pinMode(2, OUTPUT);
}
```
This block handles initial configuration:
- Configures GPIO5 to control the relay, setting it initially to `HIGH` (off).
- Connects to the WiFi network and configures mDNS for network-based naming access.
- Sets up HTTP server endpoints and starts the server for handling requests.

### Main Loop

```cpp
void loop() {
  httpServer.handleClient();  // Handles incoming client requests
  MDNS.update();              // Updates mDNS service
}
```
This loop keeps the web server responsive to client requests and maintains mDNS services.

### Web Server Endpoints

#### Root Path (`"/"`)
```cpp
void handleRootPath() {
  httpServer.send(200, "text/plain", "Hello world");
}
```
This endpoint returns a simple "Hello world" message for basic connectivity testing.

#### Unlock Path (`"/unlock"`)
```cpp
void handleUnlockPath() {
  digitalWrite(5, !digitalRead(5)); // Toggle relay state
  delay(250);                       // Wait 250ms for relay activation
  digitalWrite(5, !digitalRead(5)); // Reset relay state
  httpServer.send(200, "text/plain", "Door Unlocked");
}
```
This endpoint toggles GPIO5 (relay control pin) to unlock the door and responds with "Door Unlocked".

## OTA Update Using HTTP
You can update the ESP8266 firmware over HTTP:
```bash
curl -F "image=@firmware.bin" esp8266-doorunlock.local/update
```
Replace `"firmware.bin"` with the path to your new firmware file.

---

## Additional Notes
- **Static IP**: Uncomment `WiFi.config(staticIP, subnet, gateway, dns);` to activate the static IP configuration.
- **OTA Access**: Access OTA at `http://esp8266-doorunlock.local/update`. Replace `"esp8266-doorunlock.local"` with your hostname or IP if different.

This setup provides a secure, low-cost IoT door unlocking system. Make sure to replace `STASSID` and `STAPSK` with your network details before deploying.

#App
Apk Included

![alt text](https://raw.githubusercontent.com/AryanRai/DoorUnlock/main/AppScreenshot.png)