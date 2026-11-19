/*
  Wearable monitor: reads GPS, RTC, heart rate, and motion, counts steps,
  and publishes telemetry over BLE and Wi-Fi. The button sounds the buzzer.
  No display or battery-voltage ADC is connected in the supplied circuit.
*/

#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <TinyGPSPlus.h>
#include <RTClib.h>
#include <MAX30105.h>
#include <Adafruit_BMI270.h>
#include <Adafruit_Sensor.h>

const int GPS_RX = 1;
const int GPS_TX = 3;
const int BUTTON_PIN = 2;
const int BUZZER_PIN = 4;

const char *WIFI_SSID = "YOUR_WIFI_SSID";
const char *WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

const char *SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
const char *DATA_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

HardwareSerial gpsSerial(1);
TinyGPSPlus gps;
RTC_DS3231 rtc;
MAX30105 heart;
Adafruit_BMI270 imu;
BLECharacteristic *dataCharacteristic;

bool heartOK = false;
bool imuOK = false;
bool rtcOK = false;
bool bleConnected = false;

uint32_t lastReport = 0;
uint32_t lastStepTime = 0;
uint32_t stepCount = 0;
uint32_t heartRate = 0;
float previousMagnitude = 1.0f;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    bleConnected = true;
  }

  void onDisconnect(BLEServer *server) override {
    bleConnected = false;
    server->startAdvertising();
  }
};

void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    return;
  }

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED &&
         millis() - start < 10000) {
    delay(100);
  }
}

void startBluetooth() {
  BLEDevice::init("Wearable Monitor");

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);
  dataCharacteristic = service->createCharacteristic(
    DATA_UUID,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_NOTIFY
  );

  dataCharacteristic->addDescriptor(new BLE2902());
  dataCharacteristic->setValue("Wearable monitor ready");
  service->start();
  server->getAdvertising()->start();
}

void soundAlert() {
  tone(BUZZER_PIN, 2200, 120);
  delay(150);
  tone(BUZZER_PIN, 2800, 120);
}

void readGps() {
  while (gpsSerial.available()) {
    gps.encode(gpsSerial.read());
  }
}

void readHeartRate() {
  if (!heartOK) {
    return;
  }

  long ir = heart.getIR();
  if (ir < 30000) {
    heartRate = 0;
    return;
  }

  static uint32_t lastBeat = 0;
  static bool wasHigh = false;
  bool isHigh = ir > 50000;

  if (isHigh && !wasHigh && lastBeat != 0) {
    uint32_t interval = millis() - lastBeat;
    if (interval > 300 && interval < 2000) {
      heartRate = 60000UL / interval;
    }
  }

  if (isHigh) {
    lastBeat = millis();
  }
  wasHigh = isHigh;
}

void readSteps() {
  if (!imuOK) {
    return;
  }

  sensors_event_t accel;
  sensors_event_t gyro;
  sensors_event_t temperature;
  imu.getEvent(&accel, &gyro, &temperature);

  float magnitude = sqrt(
    accel.acceleration.x * accel.acceleration.x +
    accel.acceleration.y * accel.acceleration.y +
    accel.acceleration.z * accel.acceleration.z
  );

  bool crossing = previousMagnitude < 11.5f && magnitude >= 11.5f;
  if (crossing && millis() - lastStepTime > 300) {
    stepCount++;
    lastStepTime = millis();
  }

  previousMagnitude = magnitude;
}

void publishData() {
  DateTime now = rtcOK ? rtc.now() : DateTime(2000, 1, 1, 0, 0, 0);

  String message;
  message.reserve(180);
  message += "time=";
  message += now.timestamp(DateTime::TIMESTAMP_TIME);
  message += ",hr=";
  message += heartRate;
  message += ",steps=";
  message += stepCount;
  message += ",battery=unknown";

  if (gps.location.isValid()) {
    message += ",lat=";
    message += String(gps.location.lat(), 6);
    message += ",lon=";
    message += String(gps.location.lng(), 6);
  }

  if (bleConnected) {
    dataCharacteristic->setValue(message.c_str());
    dataCharacteristic->notify();
  }

  Serial.println(message);
}

void setup() {
  Serial.begin(115200);
  delay(500);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);

  Wire.begin();
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX, GPS_TX);

  rtcOK = rtc.begin();
  if (rtcOK && rtc.lostPower()) {
    rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
  }

  heartOK = heart.begin(Wire, I2C_SPEED_FAST);
  if (heartOK) {
    heart.setup();
    heart.setPulseAmplitudeRed(0x24);
    heart.setPulseAmplitudeGreen(0);
  }

  imuOK = imu.begin_I2C(0x68, &Wire);
  if (!imuOK) {
    imuOK = imu.begin_I2C(0x69, &Wire);
  }

  startBluetooth();
  connectWiFi();

  Serial.println("Wearable monitor started");
}

void loop() {
  readGps();
  readHeartRate();
  readSteps();

  if (digitalRead(BUTTON_PIN) == LOW) {
    soundAlert();
    while (digitalRead(BUTTON_PIN) == LOW) {
      delay(10);
    }
  }

  if (millis() - lastReport >= 1000) {
    lastReport = millis();
    publishData();
  }

  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
  }

  delay(20);
}