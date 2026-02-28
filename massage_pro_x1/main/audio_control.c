#include "audio_control.h"
#include "driver/i2s_std.h"
#include "driver/spi_common.h"
#include "driver/gpio.h"
#include "driver/ledc.h"
#include "esp_vfs_fat.h"
#include "sdmmc_cmd.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>
#include <sys/stat.h>

#define AUDIO_TAG "AUDIO"
#define SAMPLE_RATE     44100
#define BITS_PER_SAMPLE 16
#define DMA_BUF_COUNT   8
#define DMA_BUF_LEN     1024

// PWM for GAIN control (9dB and 12dB modes)
#define GAIN_PWM_TIMER      LEDC_TIMER_1
#define GAIN_PWM_MODE       LEDC_LOW_SPEED_MODE
#define GAIN_PWM_CHANNEL    LEDC_CHANNEL_1
#define GAIN_PWM_DUTY_RES   LEDC_TIMER_10_BIT

static bool audio_initialized = false;
static sdmmc_card_t *card = NULL;
static bool audio_playing = false;
static i2s_chan_handle_t tx_handle = NULL;

// Software volume control
static uint8_t current_volume = 70;  // Default 70%
static bool is_muted = false;

// WAV file header structure
typedef struct {
    char riff[4];           // "RIFF"
    uint32_t file_size;
    char wave[4];           // "WAVE"
    char fmt[4];            // "fmt "
    uint32_t fmt_size;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
    char data[4];           // "data"
    uint32_t data_size;
} wav_header_t;

// Audio file paths
static const char* audio_files[] = {
    [AUDIO_NOTIFY_STARTUP]          = "/sdcard/sounds/voice/startup.wav",
    [AUDIO_NOTIFY_BLE_CONNECTED]    = "/sdcard/sounds/voice/connected.wav",
    [AUDIO_NOTIFY_BLE_DISCONNECTED] = "/sdcard/sounds/voice/disconnected.wav",
    [AUDIO_NOTIFY_ROTATE]           = "/sdcard/sounds/voice/rotate.wav",
    [AUDIO_NOTIFY_HEAT_ON]          = "/sdcard/sounds/voice/heat_on.wav",
    [AUDIO_NOTIFY_HEAT_OFF]         = "/sdcard/sounds/voice/heat_off.wav",
    [AUDIO_NOTIFY_LEVEL_1]          = "/sdcard/sounds/voice/level_1.wav",
    [AUDIO_NOTIFY_LEVEL_2]          = "/sdcard/sounds/voice/level_2.wav",
    [AUDIO_NOTIFY_LEVEL_3]          = "/sdcard/sounds/voice/level_3.wav",
    [AUDIO_NOTIFY_LEVEL_4]          = "/sdcard/sounds/voice/level_4.wav",
    [AUDIO_NOTIFY_LEVEL_5]          = "/sdcard/sounds/voice/level_5.wav",
    [AUDIO_NOTIFY_SESSION_START]    = "/sdcard/sounds/voice/session_start.wav",
    [AUDIO_NOTIFY_SESSION_COMPLETE] = "/sdcard/sounds/voice/session_complete.wav",
    [AUDIO_NOTIFY_ONE_MINUTE_WARNING] = "/sdcard/sounds/voice/one_minute_warning.wav"
};
//-----------------------------------------------------------------------------
// MAX98357A Control Functions
//-----------------------------------------------------------------------------

static esp_err_t init_sd_control(void) {
    // Configure SD (shutdown) pin for mute control
    gpio_config_t io_conf = {
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
        .pin_bit_mask = (1ULL << I2S_SD_PIN)
    };
    esp_err_t ret = gpio_config(&io_conf);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to configure SD pin");
        return ret;
    }
    
    // Enable audio by default (SD = HIGH to enable)
    gpio_set_level(I2S_SD_PIN, 1);
    
    ESP_LOGI(AUDIO_TAG, "MAX98357A SD control initialized (GPIO%d)", I2S_SD_PIN);
    
    return ESP_OK;
}

// Software volume control - scale audio samples
void audio_set_volume(uint8_t volume) {
    if (volume > 100) {
        volume = 100;
    }
    current_volume = volume;
    ESP_LOGI(AUDIO_TAG, "Volume set to %d%%", volume);
}

uint8_t audio_get_volume(void) {
    return current_volume;
}

// Mute control using SD pin
void audio_set_mute(bool muted) {
    is_muted = muted;
    
    if (muted) {
        // SD pin LOW = shutdown (mute)
        gpio_set_level(I2S_SD_PIN, 0);
        ESP_LOGI(AUDIO_TAG, "Audio muted");
    } else {
        // SD pin HIGH = enable
        gpio_set_level(I2S_SD_PIN, 1);
        ESP_LOGI(AUDIO_TAG, "Audio unmuted");
    }
}

bool audio_is_muted(void) {
    return is_muted;
}

// Apply volume scaling to audio buffer
static void apply_volume(int16_t* buffer, size_t sample_count) {
    if (is_muted || current_volume == 0) {
        // Complete silence
        memset(buffer, 0, sample_count * sizeof(int16_t));
        return;
    }
    
    // Apply volume scaling
    for (size_t i = 0; i < sample_count; i++) {
        int32_t sample = buffer[i];
        sample = (sample * current_volume) / 100;
        
        // Clamp to prevent overflow
        if (sample > 32767) sample = 32767;
        if (sample < -32768) sample = -32768;
        
        buffer[i] = (int16_t)sample;
    }
}

//-----------------------------------------------------------------------------
// I2S Initialization
//-----------------------------------------------------------------------------

static esp_err_t init_i2s(void) {
    esp_err_t ret;

    // Create I2S channel configuration
    i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
    chan_cfg.dma_desc_num = DMA_BUF_COUNT;
    chan_cfg.dma_frame_num = DMA_BUF_LEN;
    chan_cfg.auto_clear = true;

    // Allocate new TX channel
    ret = i2s_new_channel(&chan_cfg, &tx_handle, NULL);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to create I2S channel: %s", esp_err_to_name(ret));
        return ret;
    }

    // Configure I2S standard mode
    i2s_std_config_t std_cfg = {
        .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE),
        .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO),
        .gpio_cfg = {
            .mclk = I2S_GPIO_UNUSED,
            .bclk = I2S_BCLK_PIN,
            .ws = I2S_LRC_PIN,
            .dout = I2S_DOUT_PIN,
            .din = I2S_GPIO_UNUSED,
            .invert_flags = {
                .mclk_inv = false,
                .bclk_inv = false,
                .ws_inv = false,
            },
        },
    };

    ret = i2s_channel_init_std_mode(tx_handle, &std_cfg);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to initialize I2S standard mode: %s", esp_err_to_name(ret));
        i2s_del_channel(tx_handle);
        tx_handle = NULL;
        return ret;
    }

    // Enable the channel
    ret = i2s_channel_enable(tx_handle);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to enable I2S channel: %s", esp_err_to_name(ret));
        i2s_del_channel(tx_handle);
        tx_handle = NULL;
        return ret;
    }

    ESP_LOGI(AUDIO_TAG, "I2S initialized successfully");
    ESP_LOGI(AUDIO_TAG, "  LRC (WS):  GPIO%d", I2S_LRC_PIN);
    ESP_LOGI(AUDIO_TAG, "  BCLK:      GPIO%d", I2S_BCLK_PIN);
    ESP_LOGI(AUDIO_TAG, "  DOUT (DIN): GPIO%d", I2S_DOUT_PIN);
    
    return ESP_OK;
}

//-----------------------------------------------------------------------------
// SD Card Initialization
//-----------------------------------------------------------------------------

static esp_err_t init_sd_card(void) {
    esp_err_t ret;
    
    esp_vfs_fat_sdmmc_mount_config_t mount_config = {
        .format_if_mount_failed = false,
        .max_files = 5,
        .allocation_unit_size = 16 * 1024
    };

    sdmmc_host_t host = SDSPI_HOST_DEFAULT();
    
    spi_bus_config_t bus_cfg = {
        .mosi_io_num = SD_MOSI_PIN,
        .miso_io_num = SD_MISO_PIN,
        .sclk_io_num = SD_SCK_PIN,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = 4096,
    };
    
    ret = spi_bus_initialize(host.slot, &bus_cfg, SPI_DMA_CH_AUTO);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to initialize SPI bus: %s", esp_err_to_name(ret));
        return ret;
    }

    sdspi_device_config_t slot_config = SDSPI_DEVICE_CONFIG_DEFAULT();
    slot_config.gpio_cs = SD_CS_PIN;
    slot_config.host_id = host.slot;

    ret = esp_vfs_fat_sdspi_mount("/sdcard", &host, &slot_config, &mount_config, &card);
    if (ret != ESP_OK) {
        ESP_LOGE(AUDIO_TAG, "Failed to mount SD card: %s", esp_err_to_name(ret));
        spi_bus_free(host.slot);
        return ret;
    }

    sdmmc_card_print_info(stdout, card);
    ESP_LOGI(AUDIO_TAG, "SD card mounted at /sdcard");
    
    return ESP_OK;
}

//-----------------------------------------------------------------------------
// Audio Playback Functions
//-----------------------------------------------------------------------------

esp_err_t audio_init(void) {
    ESP_LOGI(AUDIO_TAG, "Initializing audio system...");
    
    // Initialize MAX98357A SD control pin
    if (init_sd_control() != ESP_OK) {
        return ESP_FAIL;
    }
    
    // Initialize I2S
    if (init_i2s() != ESP_OK) {
        return ESP_FAIL;
    }
    
    // Initialize SD card
    if (init_sd_card() != ESP_OK) {
        if (tx_handle) {
            i2s_channel_disable(tx_handle);
            i2s_del_channel(tx_handle);
            tx_handle = NULL;
        }
        return ESP_FAIL;
    }
    
    audio_initialized = true;
    ESP_LOGI(AUDIO_TAG, "Audio system ready");
    
    return ESP_OK;
}

esp_err_t audio_play_file(const char* filepath) {
    if (!audio_initialized || !tx_handle) {
        ESP_LOGE(AUDIO_TAG, "Audio not initialized!");
        return ESP_FAIL;
    }

    // Check if muted
    if (is_muted) {
        ESP_LOGW(AUDIO_TAG, "Audio is muted, skipping playback");
        return ESP_OK;
    }

    // Check if file exists
    struct stat st;
    if (stat(filepath, &st) != 0) {
        ESP_LOGW(AUDIO_TAG, "File not found: %s", filepath);
        return ESP_ERR_NOT_FOUND;
    }

    FILE* file = fopen(filepath, "rb");
    if (!file) {
        ESP_LOGE(AUDIO_TAG, "Failed to open file: %s", filepath);
        return ESP_FAIL;
    }

    // Read WAV header
    wav_header_t header;
    if (fread(&header, sizeof(wav_header_t), 1, file) != 1) {
        ESP_LOGE(AUDIO_TAG, "Failed to read WAV header");
        fclose(file);
        return ESP_FAIL;
    }

    // Verify WAV format
    if (strncmp(header.riff, "RIFF", 4) != 0 || strncmp(header.wave, "WAVE", 4) != 0) {
        ESP_LOGE(AUDIO_TAG, "Invalid WAV file");
        fclose(file);
        return ESP_FAIL;
    }

    ESP_LOGI(AUDIO_TAG, "Playing: %s (Volume: %d%%)", filepath, current_volume);

    // Read and play audio data
    int16_t buffer[DMA_BUF_LEN];  // Use int16_t for volume processing
    size_t bytes_written;
    audio_playing = true;

    while (audio_playing) {
        size_t bytes_read = fread(buffer, 1, sizeof(buffer), file);
        if (bytes_read == 0) {
            break;  // End of file
        }

        // Apply volume scaling
        size_t sample_count = bytes_read / sizeof(int16_t);
        apply_volume(buffer, sample_count);

        esp_err_t ret = i2s_channel_write(tx_handle, buffer, bytes_read, &bytes_written, portMAX_DELAY);
        if (ret != ESP_OK) {
            ESP_LOGE(AUDIO_TAG, "I2S write failed: %s", esp_err_to_name(ret));
            break;
        }
    }

    fclose(file);
    
    // Small delay to ensure audio finishes
    vTaskDelay(100 / portTICK_PERIOD_MS);
    
    ESP_LOGI(AUDIO_TAG, "Playback finished");
    return ESP_OK;
}

esp_err_t audio_play_tone(uint16_t frequency, uint16_t duration_ms) {
    if (!audio_initialized || !tx_handle) {
        ESP_LOGE(AUDIO_TAG, "Audio not initialized!");
        return ESP_FAIL;
    }
    
    // Check if muted
    if (is_muted) {
        return ESP_OK;
    }
    
    ESP_LOGI(AUDIO_TAG, "Playing tone: %dHz for %dms (Volume: %d%%)", 
             frequency, duration_ms, current_volume);
    
    // Generate simple square wave
    uint32_t sample_count = (SAMPLE_RATE * duration_ms) / 1000;
    uint32_t half_period = SAMPLE_RATE / (2 * frequency);
    
    int16_t buffer[DMA_BUF_LEN];
    size_t bytes_written;
    int16_t value = 16000;  // Amplitude
    uint32_t counter = 0;
    
    audio_playing = true;
    
    for (uint32_t i = 0; i < sample_count && audio_playing; i += DMA_BUF_LEN) {
        size_t samples_to_write = (sample_count - i > DMA_BUF_LEN) ? DMA_BUF_LEN : (sample_count - i);
        
        for (size_t j = 0; j < samples_to_write; j++) {
            buffer[j] = (counter++ / half_period) % 2 ? value : -value;
        }
        
        // Apply volume scaling
        apply_volume(buffer, samples_to_write);
        
        esp_err_t ret = i2s_channel_write(tx_handle, buffer, samples_to_write * sizeof(int16_t), 
                                         &bytes_written, portMAX_DELAY);
        if (ret != ESP_OK) {
            ESP_LOGE(AUDIO_TAG, "I2S write failed: %s", esp_err_to_name(ret));
            break;
        }
    }
    
    return ESP_OK;
}

esp_err_t audio_notify(audio_notify_type_t type) {
    if (!audio_initialized) {
        ESP_LOGW(AUDIO_TAG, "Audio not initialized, skipping notification");
        return ESP_FAIL;
    }
    
    if (type >= sizeof(audio_files) / sizeof(audio_files[0])) {
        ESP_LOGE(AUDIO_TAG, "Invalid notification type: %d", type);
        return ESP_ERR_INVALID_ARG;
    }
    
    const char* filepath = audio_files[type];
    
    // Special handling for level notifications
    if (type >= AUDIO_NOTIFY_LEVEL_1 && type <= AUDIO_NOTIFY_LEVEL_5) {
        // Try voice file first
        struct stat st;
        if (stat(filepath, &st) == 0) {
            return audio_play_file(filepath);
        }
        
        // Fallback: play beeps
        int level = type - AUDIO_NOTIFY_LEVEL_1 + 1;
        for (int i = 0; i < level; i++) {
            audio_play_file("/sdcard/sounds/beep.wav");
            vTaskDelay(200 / portTICK_PERIOD_MS);
        }
        return ESP_OK;
    }
    
    return audio_play_file(filepath);
}

void audio_stop(void) {
    audio_playing = false;
    
    if (tx_handle) {
        // Preload zero data to clear DMA buffer
        uint8_t zero_buffer[DMA_BUF_LEN * 2] = {0};
        size_t bytes_written;
        i2s_channel_preload_data(tx_handle, zero_buffer, sizeof(zero_buffer), &bytes_written);
    }
    
    ESP_LOGI(AUDIO_TAG, "Audio stopped");
}
