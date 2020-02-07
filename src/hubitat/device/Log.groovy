package hubitat.device

class Log {
    void debug(message) {
        print("DEBUG - " + message)
    }

    void info(message) {
        print("INFO - " + message)
    }

    void error(message) {
        print("ERROR - " + message)
    }

    void warn(message) {
        print("WARN - " + message)
    }
}
