/*
  To upload through terminal you can use: curl -F "image=@firmware.bin" esp8266-webupdate.local/update
*/

#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <ESP8266mDNS.h>
#include <ESP8266HTTPUpdateServer.h>

#ifndef STASSID
#define STASSID "xyz"
#define STAPSK  "xyz"
#endif

const char* host = "esp8266-doorunlock";
const char* ssid = STASSID;
const char* password = STAPSK;

//Static IP address configuration
IPAddress staticIP(10, 0, 0, 55); //ESP static ip
IPAddress gateway(10, 0, 0, 1);   //IP Address of your WiFi Router (Gateway)
IPAddress subnet(255, 255, 255, 0);  //Subnet mask
IPAddress dns(10, 0, 0, 1);  //DNS

ESP8266WebServer httpServer(80);
ESP8266HTTPUpdateServer httpUpdater;

void setup(void) {
  pinMode(5,OUTPUT);
  digitalWrite(5,HIGH);
  Serial.begin(115200);
  Serial.println();
  Serial.println("Booting Sketch...");
  WiFi.disconnect();
  WiFi.mode(WIFI_AP_STA);
  WiFi.hostname(host);
//  WiFi.config(staticIP, subnet, gateway, dns);
  WiFi.begin(ssid, password);

  while (WiFi.waitForConnectResult() != WL_CONNECTED) {
    WiFi.begin(ssid, password);
    Serial.println("WiFi failed, retrying.");
  }

  MDNS.begin(host);

  httpServer.on("/", handleRootPath);
  httpServer.on("/unlock", handleUnlockPath);

  
  httpUpdater.setup(&httpServer);
  httpServer.begin();

  MDNS.addService("http", "tcp", 80);
  Serial.printf("HTTPUpdateServer ready! Open http://%s.local/update in your browser\n", host);
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
  Serial.print("This is new firmware");
  pinMode(2,OUTPUT);

}

void loop(void) {
  httpServer.handleClient();
  MDNS.update();
}

void handleRootPath() {            //Handler for the rooth path
  httpServer.send(200, "text/plain", "Hello world");
}

void handleUnlockPath() {            //Handler for the unlock path
  digitalWrite(5,!digitalRead(5));
  delay(250);
  digitalWrite(5,!digitalRead(5));
  httpServer.send(200, "text/plain", "Door Unloack");
}
