# main/CMakeLists.txt
idf_component_register(SRCS "massage_pro_x1.c" "max30102.c" "audio_control.c" "assistant_handler.c" "motor_control.c" "ble_server.c" "command_processor.c"
                    INCLUDE_DIRS ".")
