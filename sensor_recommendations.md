# Smartwatch Hardware & Sensor Recommendations

Based on the project requirements in [`README.md`](./README.md) and the companion Android app in [`phone_app/`](./phone_app/) (which already targets an **ESP32** over BLE via [`Esp32BleManager.kt`](./phone_app/app/src/main/java/com/example/prova_android/Esp32BleManager.kt)), this report outlines the best, cost-effective components to buy for building the smartwatch.

---

## Quick Summary: Two Ways to Build This

Before buying individual parts, decide which architecture fits your course rules and timeline:

| Approach | Best For | Estimated Total Cost | Effort |
| :--- | :--- | :---: | :---: |
| **Option A: Modular Build** *(ESP32-S3 + individual breakout boards)* | Breadboard prototyping, custom PCB design, or courses requiring discrete sensor wiring | **€25 – €45** *(Budget: ~€20)* | Medium–High |
| **Option B: Integrated Smartwatch Board** *(Waveshare ESP32-S3-Touch-LCD-1.69 + GPS + HR + LiPo)* | Compact wearable enclosure, fast firmware progress, minimal wiring headaches | **€28 – €40** | Low–Medium |

> **Tip:** If your professor allows integrated dev boards, **Option B** already includes the ESP32-S3 (Wi-Fi + BLE), 1.69" capacitive touch screen, 6-axis IMU, RTC, buzzer, LiPo charger, and battery ADC on a single smartwatch-sized PCB — you only need to add a Heart Rate sensor, GPS module, and LiPo battery.

---

## Component-by-Component Recommendations

### 1. Microcontroller (BLE + Wi-Fi)
*Requirements: Bluetooth Low Energy, Wi-Fi, cheap, compact smartwatch size, compatible with `Esp32BleManager.kt`*

* **Recommended:** **Seeed Studio XIAO ESP32S3** — **~€7 – €9**
  * **Why:** Ultra-compact (21 × 17.5 mm — great for a smartwatch), built-in **Wi-Fi (2.4 GHz)** + **Bluetooth 5.0 LE**, **8 MB PSRAM + 8 MB Flash** (plenty for LVGL touch UIs), **native USB-C** (leaves hardware UART `D6`/`D7` free for GPS), and a **built-in LiPo battery charger** (`BAT+`/`BAT-` pads on the back, so you don't need an external TP4056 or 3.3V LDO!).
  * **Pin Budget Check:** It has **11 GPIOs (`D0`–`D10`)**, which fits the entire project cleanly—see the [XIAO ESP32S3 Complete Pinout Plan](#xiao-esp32s3-complete-pinout-plan-11--11-pins) below.
* **More-Pins / Budget Alternative:** **ESP32-S3-DevKitC-1 (N16R8)** (~€5 – €7) or **ESP32-WROOM-32 DevKit v1** (~€3 – €5)
  * Useful only if you want extra spare GPIOs for additional buttons, external interrupts, or a GPS power-cutoff MOSFET without tight pin budgeting. *(Requires an external TP4056 charger + low-dropout 3.3V regulator for LiPo power.)*

---

### 2. Accelerometer + Gyroscope (6-Axis IMU for Step Counting)
*Requirements: Accelerometer, gyroscope, step counting*

Instead of buying two separate sensors, buy a single **6-axis IMU** (3-axis accelerometer + 3-axis gyroscope).

* **Recommended:** **BMI270** or **LSM6DSO / LSM6DS3** breakout — **~€3 – €6**
  * **Interface:** I2C or SPI (3.3V)
  * **Why:** Specifically designed for wearables — ultra-low power (~700 µA active) and includes a **built-in hardware step counter (pedometer)** and wrist-tilt wake detection. Offloading step counting to the IMU saves CPU time, battery, and debugging effort.
* **Ultra-Budget Alternative:** **MPU-6050** (GY-521) or **MPU-6500** — **~€1.50 – €3**
  * **Why:** Dirt cheap with hundreds of ESP32 tutorials.
  * **Trade-offs:** No built-in hardware pedometer (you must write peak-detection/filtering in firmware), higher power consumption (~3.5 mA), and the MPU-6050 is officially EOL (most cheap modules use clones, though they still work fine for student projects).

---

### 3. Heart Rate Sensor
*Requirements: Heart rate sensor, display non-instantaneous (smoothed/averaged) heart beats*

* **Recommended (Best Value):** **MAX30102** breakout — **~€2 – €4**
  * **Interface:** I2C (3.3V, address `0x57`)
  * **Why:** Cheap, tiny, uses optical PPG (Red + Infrared LEDs), measures both **BPM** and **SpO₂**, and works out of the box with the **SparkFun MAX3010x** Arduino/ESP-IDF library. Easy to compute a rolling average (e.g., 5–10 second window) to satisfy your *"not instantaneous"* requirement.
* **Better-for-Wrist Alternative:** **MAX30101** (~€10 – €15) or **Pulse Sensor (Green LED analog PPG, SEN-11574 clone)** (~€3 – €5)
  * **Important "Wrist Problem" Caveat:** Commercial smartwatches use **Green LEDs** (like the MAX30101 or analog Pulse Sensor) because green light is absorbed strongly near the skin surface and resists motion noise better on the wrist. The cheaper **MAX30102** (Red + IR) is designed primarily for **fingertip** measurements.
  * **How to make MAX30102 work reliably:**
    1. Keep the sensor pressed snugly against the skin with an opaque foam ring/housing to block ambient light leakage.
    2. Use a moving-average / median filter and reject readings when the IMU detects heavy arm movement.
    3. For demos, placing a finger directly on the sensor gives rock-solid readings within seconds.

---

### 4. GPS Module
*Requirements: GPS, battery-friendly, cheap*

* **Recommended:** **ATGM336H** (with ceramic patch antenna) — **~€5 – €8**
  * **Interface:** UART (TX/RX, 3.3V, standard NMEA sentences compatible with `TinyGPS++`)
  * **Why:** Roughly **half the size** (~13 × 16 mm) and **half the power draw** (~20–25 mA tracking) of a NEO-6M, plus **Multi-GNSS** support (GPS + BeiDou/GLONASS) for faster, more reliable satellite locks outdoors.
* **Budget / Breadboard Alternative:** **u-blox NEO-6M (GY-NEO6MV2)** — **~€3 – €6**
  * **Why:** Ubiquitous, cheap, tons of guides.
  * **Trade-offs:** Larger PCB (~25 × 35 mm) + external ceramic antenna, GPS-only, and pulls **45–67 mA** continuously.
* **Power Tip:** Regardless of which GPS you buy, GPS is one of the biggest battery drains. Consider adding a cheap **P-channel MOSFET** (e.g., AO3401, ~€0.10) controlled by an ESP32 GPIO so you can completely power off the GPS when not actively recording a workout/location.

---

### 5. Real-Time Clock (RTC) Module
*Requirements: Real-time clock module, display current time*

* **Recommended:** **DS3231 Mini Module** (e.g., DS3231M / ZS-042) — **~€2 – €3**
  * **Interface:** I2C (3.3V, address `0x68`)
  * **Why:** Temperature-compensated crystal oscillator (**±2 ppm** accuracy — drifts less than ~1 minute per year) with battery backup so time persists across deep sleep and power-off.
  * **Hardware Warning (ZS-042 boards):** Cheap ZS-042 modules include a trickle-charging circuit meant for rechargeable LIR2032 cells. If you insert a standard non-rechargeable **CR2032**, remove the small charging resistor/diode on the board (or buy the compact *DS3231 Mini* board with a soldered backup cell).
* **Compact / Ultra-Low-Power Alternative:** **PCF8563** or **PCF85063** — **~€1.50 – €3**
  * Tiny footprint, nanoamp backup draw, and uses I2C address `0x51` (avoiding the `0x68` collision with MPU-6050/BMI270).
* **Sync Tip:** You can periodically auto-sync the RTC using **Wi-Fi (NTP)**, **GPS UTC time**, or a timestamp sent over **BLE** from your Android app.

---

### 6. Display (Touchscreen)
*Requirements: Screen (possibly touch) displaying battery %, heart beats, steps, and current time*

* **Recommended (Easiest UI Layout):** **1.69" ST7789 IPS LCD (240×280) + CST816S/CST816D Capacitive Touch** — **~€7 – €11**
  * **Interface:** SPI (display) + I2C (touch, address `0x15`)
  * **Why:** Rounded-rectangle smartwatch aspect ratio makes laying out time, BPM, steps, and battery bar clean and readable. Full support in **TFT_eSPI**, **LovyanGFX**, and **LVGL**.
* **Classic Watch Alternative:** **1.28" Round GC9A01 IPS LCD (240×240) + CST816S Capacitive Touch** — **~€8 – €12**
  * Same SPI + I2C workflow, great circular smartwatch aesthetic (slightly trickier to fit 4 text metrics without clipping corners).
* **Ultra-Budget Non-Touch Fallback:** **0.96" SSD1306** or **1.3" SH1106 OLED** — **~€2 – €3**
  * Monochrome, I2C-only (saves GPIOs), very low power, high contrast. Pair with 1–2 tactile pushbuttons instead of touch.

---

### 7. Buzzer
*Requirements: Buzzer to make sounds*

* **Recommended:** **3.3V Passive Piezo Buzzer** (9 mm or 12 mm, or SMD 5×5 mm) — **~€0.30 – €1**
  * **Why:** Driven via ESP32 PWM (`ledcWriteTone`), allowing different frequencies, beeps, alarms, and notification jingles (e.g., when hitting the 10,000-step goal in `FitnessData.kt`).
  * **Driver Circuit:** Drive it through a small NPN transistor (**2N2222 / S8050**) or N-MOSFET (**2N7002**) with a 1 kΩ base resistor rather than directly from an ESP32 GPIO to protect the pin and get higher volume.
* **Simpler Alternative:** **3.3V Active Buzzer** (~€0.30 – €1) — single fixed tone, just drive HIGH/LOW.

---

### 8. Battery, Charging & Battery Percentage
*Requirements: Long battery (at least a couple of hours), display battery percentage*

* **Battery:** **3.7V LiPo Battery, 500 mAh – 1000 mAh** (e.g., *602535* 500 mAh or *803040* 1000 mAh, with built-in PCM protection) — **~€4 – €7**
* **Charger Module:** **TP4056 USB-C Charger with Protection (DW01A)** — **~€1**
  * *(Skip if using a dev board with built-in LiPo charging like Waveshare or XIAO ESP32S3.)*
  * If powering a bare ESP32-S3 directly from a 3.7V LiPo, ensure your dev board has a low-dropout 3.3V regulator (e.g., **AP2112K-3.3** or **ME6211**) — avoid standard AMS1117 boards on LiPo because AMS1117 has a ~1.1V dropout and browns out before the battery is empty.
* **Battery Percentage Sensor:**
  * **Recommended:** **MAX17043 / MAX17048 I2C Fuel Gauge** — **~€2 – €4** (I2C address `0x36`). Gives accurate **State-of-Charge (%)** directly over I2C with almost zero current draw (~3 µA) and no ADC calibration headaches.
  * **Cheapest Alternative:** **Voltage Divider (2 × 100 kΩ resistors)** connected from `VBAT` to an ESP32 ADC pin + a simple LiPo voltage-to-percentage lookup table (costs cents, less accurate under sudden load spikes).

#### Quick Battery Life Estimate (600 mAh LiPo)
| Operating Profile | Approx. Avg. Current | Estimated Runtime |
| :--- | :---: | :---: |
| **Everything ON continuously** (ESP32 active + BLE + LCD backlight + GPS + MAX30102 + IMU) | ~140 – 180 mA | **~3.3 – 4.3 hours** |
| **Smart Duty Cycling** (Screen turns off after 15s, Wi-Fi off unless syncing, GPS off unless tracking, BLE low-power advertising) | ~35 – 60 mA | **~10 – 17 hours** |

Even a compact **500–600 mAh** LiPo comfortably exceeds the *"at least a couple of hours"* requirement, and a **1000 mAh** cell will run all day.

---

## Option B Deep Dive: Integrated Smartwatch Board

If you want to skip wiring the display, IMU, RTC, buzzer, and battery charger individually, the **Waveshare ESP32-S3-Touch-LCD-1.69** (~€16 – €25) is an almost exact match for your `README.md`:

* **Already Onboard:**
  * ESP32-S3R8 (Wi-Fi + BLE 5.0, 16 MB Flash, 8 MB PSRAM)
  * 1.69" IPS Capacitive Touch Display (240×280, ST7789V2 + CST816T)
  * **QMI8658C** 6-axis IMU (Accelerometer + Gyroscope)
  * **PCF85063** RTC (with backup battery header)
  * Onboard **Buzzer** (`GPIO42`)
  * **ETA6098** LiPo battery charger + MX1.25 connector + Battery Voltage ADC (`GPIO1`)
* **What You Still Need to Buy & Wire to Exposed Pads:**
  1. **MAX30102** Heart Rate sensor → wire to exposed pads (`GPIO17`/`GPIO18` as a second I2C bus `Wire1`, or tap shared I2C `GPIO10`/`GPIO11`; address `0x57` has no conflict)
  2. **ATGM336H** GPS module → wire UART to exposed pads (`GPIO2`/`GPIO3` or `GPIO43`/`GPIO44`)
  3. **3.7V LiPo Battery (500–800 mAh)** with MX1.25 connector (verify polarity before plugging in!)

---

## Estimated Bill of Materials (BOM)

### Option A1: Modular Recommended Build (Seeed XIAO ESP32S3)
| Category | Component | Interface | Est. Price |
| :--- | :--- | :--- | :---: |
| MCU (Wi-Fi + BLE + LiPo Charger) | Seeed Studio XIAO ESP32S3 | — | €8 |
| 6-Axis IMU | BMI270 or LSM6DS3 | I2C (`D4`/`D5`) | €4 |
| Heart Rate | MAX30102 | I2C (`D4`/`D5`) | €3 |
| GPS | ATGM336H + Antenna | UART (`D6`/`D7`) | €6 |
| RTC | DS3231 Mini or PCF8563 | I2C (`D4`/`D5`) | €2.50 |
| Touch Display | 1.69" ST7789 + CST816S | SPI + I2C | €9 |
| Buzzer | 3.3V Passive Piezo + 2N2222 | PWM (`D9`) | €0.50 |
| Battery | 3.7V 600–1000 mAh LiPo *(solders to XIAO `BAT+`/`BAT-`)* | Onboard Charger | €5 |
| Battery % Sensor | MAX17048 Fuel Gauge *(0 extra pins!)* | I2C (`D4`/`D5`) | €3 |
| **Total** | | | **~€41** |

---

## XIAO ESP32S3 Complete Pinout Plan (11 / 11 Pins)

The **Seeed Studio XIAO ESP32S3** exposes **11 GPIOs (`D0`–`D10`)**. Everything fits cleanly because:
1. All **5 I2C devices** (IMU, Heart Rate, RTC, Battery Fuel Gauge, and Touch controller) share just **2 pins** (`D4`/`D5`).
2. The **GPS module** uses hardware UART (`D6`/`D7`) — note that the **RTC uses I2C**, while the **GPS uses RX/TX** (though GPS also provides UTC time!). Native USB-C handles flashing and Serial Monitor, leaving `D6`/`D7` completely free.
3. An SPI LCD is **write-only**, so SPI `MISO` (`D9`) is unused and reclaimed for the **buzzer**!

| XIAO Pin | ESP32-S3 GPIO | Assignment | Connected To |
| :--- | :--- | :--- | :--- |
| **D0** | `GPIO1` | `LCD_CS` | Display Chip Select |
| **D1** | `GPIO2` | `LCD_DC` | Display Data/Command |
| **D2** | `GPIO3` | `LCD_RST` | Display Reset *(can also be tied to Touch `RST`)* |
| **D3** | `GPIO4` | `LCD_BL` | Display Backlight PWM *(dim/sleep to save battery)* |
| **D4** | `GPIO5` | **I2C SDA** | IMU + MAX30102 + RTC + MAX17048 + CST816S Touch |
| **D5** | `GPIO6` | **I2C SCL** | IMU + MAX30102 + RTC + MAX17048 + CST816S Touch |
| **D6** | `GPIO43` | **UART TX** | GPS `RX` (`ATGM336H` / `NEO-6M`) |
| **D7** | `GPIO44` | **UART RX** | GPS `TX` (`ATGM336H` / `NEO-6M`) |
| **D8** | `GPIO7` | **SPI SCK** | Display Clock (`SCK` / `SCL`) |
| **D9** | `GPIO8` | **Buzzer PWM** | Passive Buzzer via NPN transistor *(reclaimed from unused SPI MISO)* |
| **D10** | `GPIO9` | **SPI MOSI** | Display Data (`MOSI` / `SDA`) |
| **BAT+ / BAT-** | *(bottom pads)* | **LiPo Battery** | 3.7V LiPo *(charged automatically via USB-C at ~110 mA)* |

> **Need 1–2 extra pins** (e.g., for Touch `INT`, a physical wake button, or a GPS power-cutoff MOSFET)?
> * **Trick 1:** Wire `LCD_RST` (and Touch `RST`) to the XIAO's reset line or an RC reset network instead of `D2` → frees **`D2`**.
> * **Trick 2:** If using an **I2C OLED** (like 1.3" SH1106) instead of an SPI TFT, `D0`–`D3`, `D8`, and `D10` are all freed (**6 spare pins**).
> * **Battery % Tip:** Using the **MAX17048** I2C fuel gauge on `D4`/`D5` takes **0 extra pins** (whereas a resistor voltage divider would require a free ADC pin).

### Option A2: Modular Ultra-Budget Build (Minimum Cost)
| Category | Component | Interface | Est. Price |
| :--- | :--- | :--- | :---: |
| MCU (Wi-Fi + BLE) | ESP32 DevKit v1 | — | €4 |
| 6-Axis IMU | MPU-6050 (GY-521) | I2C | €2 |
| Heart Rate | MAX30102 | I2C | €2.50 |
| GPS | u-blox NEO-6M | UART | €4 |
| RTC | DS3231 | I2C | €2 |
| Display | 1.3" SH1106 OLED *(non-touch)* + 2 buttons | I2C | €3 |
| Buzzer | 3.3V Passive Piezo + Transistor | PWM | €0.50 |
| Battery + % | 3.7V 600 mAh LiPo + TP4056 + 2×100kΩ divider | ADC | €5 |
| **Total** | | | **~€23** |

### Option B: Integrated Waveshare Build (Fastest & Most Wearable)
| Category | Component | Interface | Est. Price |
| :--- | :--- | :--- | :---: |
| Base Smartwatch Board | Waveshare ESP32-S3-Touch-LCD-1.69 *(MCU, Touch LCD, IMU, RTC, Buzzer, Charger, Bat ADC)* | — | €20 |
| Heart Rate | MAX30102 | I2C (exposed pads) | €3 |
| GPS | ATGM336H + Antenna | UART (exposed pads) | €6 |
| Battery | 3.7V 600 mAh LiPo (MX1.25 connector) | MX1.25 | €5 |
| **Total** | | | **~€34** |

*(Prices are typical AliExpress / European maker-shop estimates excluding shipping.)*

---

## Important Integration Watch-Outs

1. **I2C Address Clash (`0x68`) — Modular Build:**
   * Both the **DS3231 RTC** and the **MPU-6050 / BMI270** default to I2C address `0x68`.
   * **Fix:** Tie the `AD0` (MPU-6050) or `SDO` (BMI270) pin to `3.3V` to shift the IMU to `0x69`.
   * Full conflict-free I2C map:
     * `0x15` — CST816S Touch Controller
     * `0x36` — MAX17048 Battery Fuel Gauge
     * `0x57` — MAX30102 Heart Rate Sensor
     * `0x68` — DS3231 RTC *(or `0x51` if using PCF85063)*
     * `0x69` — BMI270 / MPU-6050 *(or `0x6A`/`0x6B` for LSM6DS3 / QMI8658)*
2. **3.3V Logic Everywhere:**
   * All ESP32 pins are **3.3V only (not 5V tolerant)**. Double-check that your GPS, IMU, RTC, display, and MAX30102 breakout boards run at 3.3V logic (all recommended parts above do).
3. **Matching Your Existing Android App (`phone_app`):**
   * [`Esp32BleManager.kt`](./phone_app/app/src/main/java/com/example/prova_android/Esp32BleManager.kt) scans for BLE service UUID `4fafc201-1fb5-459e-8fcc-c5c9c331914b` and characteristic `beb5483e-36e1-4688-b7f5-ea07361b26a8`, parsing messages formatted like `BPM:72,STEPS:1250` ([`FitnessData.kt`](./phone_app/app/src/main/java/com/example/prova_android/FitnessData.kt)).
   * Any ESP32 / ESP32-S3 board works seamlessly with this using the `NimBLE-Arduino` or `ESP32 BLE Arduino` library (`NimBLE` is recommended because it uses significantly less RAM/Flash when combined with Wi-Fi and a display).
