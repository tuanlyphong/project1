// ============================================
// ESP32 PINOUT - Tối ưu hóa theo vị trí
// ============================================

// ===== BÊN TRÁI (TOP → BOTTOM) =====

// I2S Audio - MAX98357A (GPIO 16-17 gần nhau)

# define I2S_BCLK_PIN 17 // Bit clock

# define I2S_LRC_PIN 16 // Word select (left/right clock)

// SD Card SPI - Nhóm liền kề (GPIO 18-19-23)

# define SD_SCK_PIN 18 // SPI Clock

# define SD_MISO_PIN 19 // SPI MISO

# define SD_MOSI_PIN 23 // SPI MOSI

# define SD_CS_PIN 5 // Chip Select (chuyển từ GPIO15 → GPIO5)

// I2C - Giữ nguyên (GPIO 21-22 gần nhau)

# define I2C_MASTER_SDA_IO 21 // I2C SDA

# define I2C_MASTER_SCL_IO 22 // I2C SCL

// Motor Control - Nhóm 3 chân liền kề (GPIO 25-26-27)

# define MOTOR_PWM_PIN GPIO_NUM_25 // PWM Motor

# define MOTOR_IN1_PIN GPIO_NUM_26 // Motor Direction 1

# define MOTOR_IN2_PIN GPIO_NUM_27 // Motor Direction 2

// Button & LED - Nhóm 4 chân HOÀN HẢO (GPIO 32-33-GND gần nhau)

# define KEY_ADC_PIN GPIO_NUM_32 // ADC Key (có pull-up nội)

# define ADC_CHANNEL ADC1_CHANNEL_4 // ADC1_CH4

# define LED_RED_PIN GPIO_NUM_33 // LED Red

# define LED_GREEN_PIN GPIO_NUM_15 // LED Green (chuyển từ GPIO26)

// ===== BÊN PHẢI (TOP → BOTTOM) =====

// Audio & Heat - Nhóm gần nhau

# define I2S_DOUT_PIN 4 // I2S Data out (DIN on MAX98357A)

# define I2S_SD_PIN 2 // I2S Shutdown control (chuyển từ GPIO4 → GPIO2)

# define HEAT_PIN GPIO_NUM_14 // Heater control

// ============================================
// SƠ ĐỒ VỊ TRÍ CHÂN TRÊN ESP32
// ============================================

/\*
BÊN TRÁI (từ trên xuống): BÊN PHẢI (từ trên xuống):
┌─────────────────────┐ ┌─────────────────────┐
│ EN │ │ GPIO 36 │
│ GPIO 36 (input) │ │ GPIO 39 │
│ GPIO 39 (input) │ │ GPIO 34 │
│ GPIO 34 (input) │ │ GPIO 35 │
│ GPIO 35 (input) │ │ GPIO 32 ← KEY 🎮 │
│ GPIO 32 ← KEY 🎮 │ │ GPIO 33 ← LED_R 🔴 │
│ GPIO 33 ← LED_R 🔴 │ │ GPIO 25 ← MOTOR_PWM│
│ GPIO 25 ← MOTOR_PWM│ │ GPIO 26 ← MOTOR_IN1│
│ GPIO 26 ← MOTOR_IN1│ │ GPIO 27 ← MOTOR_IN2│
│ GPIO 27 ← MOTOR_IN2│ │ GPIO 14 ← HEAT 🔥 │
│ GPIO 14 ← HEAT 🔥 │ │ GPIO 12 │
│ GPIO 12 │ │ GND │
│ GND ← Button GND 🎮│ │ GPIO 13 │
│ GPIO 13 │ │ GPIO 9 │
│ GPIO 9 │ │ GPIO 10 │
│ GPIO 10 │ │ GPIO 11 │
│ GPIO 11 │ │ VIN │
│ VIN │ └─────────────────────┘
└─────────────────────┘

BOTTOM ROW (USB phía dưới - từ trái sang phải):
GPIO 15 ← LED_G 🟢 | GPIO 2 ← I2S_SD 🔇 | GPIO 0 | GPIO 4 ← I2S_DOUT 🔊
GPIO 16 ← I2S_LRC | GPIO 17 ← I2S_BCLK | GPIO 5 ← SD_CS 💾
GPIO 18 ← SD_SCK | GPIO 19 ← SD_MISO | GPIO 21 ← I2C_SDA | GPIO 3
GPIO 1 | GPIO 22 ← I2C_SCL | GPIO 23 ← SD_MOSI
\*/

// ============================================
// NHÓM CHỨC NĂNG
// ============================================

/\*
📦 AUDIO (I2S MAX98357A):

- GPIO 17: BCLK
- GPIO 16: LRC
- GPIO 4: DOUT
- GPIO 2: SD (Shutdown)

💾 SD CARD (SPI):

- GPIO 18: SCK
- GPIO 19: MISO
- GPIO 23: MOSI
- GPIO 5: CS

⚙️ MOTOR:

- GPIO 25: PWM
- GPIO 26: IN1
- GPIO 27: IN2

🎮 BUTTON & LED (4 chân gần nhau):

- GPIO 32: KEY (ADC)
- GPIO 33: LED Red
- GPIO 15: LED Green
- GND: Ground

🔥 HEATER:

- GPIO 14: Heat control
  \*/

// ============================================
// THAY ĐỔI SO VỚI PINOUT CŨ
// ============================================

/\*
CŨ → MỚI:
✅ I2S_SD_PIN: GPIO 4 → GPIO 2 (giải phóng GPIO4 cho I2S_DOUT)
✅ SD_CS_PIN: GPIO 15 → GPIO 5 (giải phóng GPIO15 cho LED_GREEN)
✅ LED_RED_PIN: GPIO 25 → GPIO 33 (GPIO25 dùng cho motor)
✅ LED_GREEN_PIN: GPIO 26 → GPIO 15 (GPIO26 dùng cho motor)

Các chân khác GIỮ NGUYÊN!
\*/
