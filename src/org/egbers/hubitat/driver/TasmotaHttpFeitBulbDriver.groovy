//Originally Based off https://community.hubitat.com/t/release-tasmota-sonoff-hubitat-driver-device-support/15445/30

metadata {
    definition(name: "Feit Light Bulb", namespace: "MRE", author: "megbers", ocfDeviceType: "oic.d.light") {
        capability "Actuator"
        capability "Bulb"
        capability "Initialize"
        capability "ColorMode"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "SwitchLevel"
        capability "Refresh"
    }

    preferences {
        section("Host") {
            input(name: "ipAddress", type: "string", title: "IP Address", displayDuringSetup: true, required: true)
            input(name: "port", type: "number", title: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
        }

        section("Authentication") {
            input(name: "username", type: "string", title: "Username", displayDuringSetup: false, required: false)
            input(name: "password", type: "password", title: "Password (sent cleartext)", displayDuringSetup: false, required: false)
        }
    }
}

def setColor(colormap) {
    log.info colormap

    def h = (360*colormap.hue)/100
    def s = colormap.saturation
    def l = colormap.level
    state.colorMode = "RGB"
    sendEvent(name: "colorMode", value: "RGB")
    sendCommand("HsbColor", "$h,$s,$l")
}

def setColorTemperature(value) {
    log.debug "setColorTemperature('${value}')"
    if(device.currentValue('colorTemperature') != value ) sendEvent(name: "colorTemperature", value: value)
    // 153..500 = set color temperature from 153 (cold) to 500 (warm) for CT lights
    // Tasmota use mired to measure color temperature
    t = value != null ?  (value as Integer) : 0
    // First make sure we have a Kelvin value we can more or less handle
    // 153 mired is approx. 6536K
    // 500 mired = 2000K
    if(t > 6536) t = 6536
    if(t < 2000) t = 2000
    t = Math.round(1000000/t)
    if(t < 153) t = 153
    if(t > 500) t = 500
    state.mired = t
    state.hue = 0
    state.saturation = 0
    state.colorMode = "CT"
    log.debug "setColorTemperature('${t}') ADJUSTED to Mired"
    sendEvent(name: "colorMode", value: "CT")
    sendEvent(name: "colorTemperature", value: "${t}")

    sendCommand("CT", "${t}")
}

def initialize() {
    //sendCommand("Backlog%20Color1%200,0,0,0,255%3B%20HsbColor%20298,9,0%3B", null)
    //sendCommand("Color1", "#00000000FF")
    //sendCommand("HsbColor", "298,9,0")
    //sendCommand("White", "100")
    //sendCommand("Dimmer2", "100")
    sendCommand("Status", "11")
}

def setHue(hue) {
    sendCommand("HsbColor1", "$hue")
}

def setSaturation(saturation) {
    sendCommand("HsbColor2", "$saturation")
}

def setLevel(level) {
    sendCommand("HsbColor3", "$level")
}

def on() {
    sendCommand("Power", "On")
}

def off() {
    sendCommand("Power", "Off")
}

def refresh() {
    log.debug "Refresh Called"
    initialize()
}

private def sendCommand(String command, String payload) {
    log.debug "sendCommand(${command}:${payload}) to device at $ipAddress:$port"

    if (!ipAddress || !port) {
        log.warn "aborting. ip address or port of device not set"
        return null;
    }
    def hosthex = convertIPtoHex(ipAddress)
    def porthex = convertPortToHex(port)
    device.deviceNetworkId = "$hosthex:$porthex:0"

    def path = "/cm"
    if (payload){
        path += "?cmnd=${command}%20${payload}"
    }
    else{
        path += "?cmnd=${command}"
    }

    if (username){
        path += "&user=${username}"
        if (password){
            path += "&password=${password}"
        }
    }

    log.debug "http://$ipAddress:$port/$path"

    def result = new hubitat.device.HubAction(
            method: "GET",
            path: path,
            headers: [
                    HOST: "${ipAddress}:${port}"
            ]
    )
    return result
}

private String convertIPtoHex(ipAddress) {
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
    String hexport = port.toString().format('%04x', port.toInteger())
    return hexport
}

private def parse(String description) {
    def message = parseLanMessage(description)
    def resultJson = message.json
    log.debug "parsing the response --> $resultJson"

// consume and set switch state
    if ((resultJson?."POWER" in ["ON", 1, "1"])) {
        setSwitchState(true)
    }
    else if ((resultJson?."POWER" in ["OFF", 0, "0"])) {
        setSwitchState(false)
    }
    if (resultJson?."HSBColor") {
        setHslState(resultJson.HSBColor)
    }
    else if (resultJson.StatusSTS?."HSBColor") {
        setHslState(resultJson.StatusSTS.HSBColor)
    }
//else {
// log.error "can not parse result with header: $message.header"
// log.error "...and raw body: $message.body"
//}
}

private def setSwitchState(Boolean on) {
    log.info "switch is " + (on ? "ON" : "OFF")
    sendEvent(name: "switch", value: on ? "on" : "off")
}

private def setHslState(hsl) {
    log.info "hslArray is $hsl"
    def hslArray = hsl.split(",")
    sendEvent(name: "level", value: hslArray[2])
    sendEvent(name: "saturation", value: hslArray[1])
    sendEvent(name: "hue", value: hslArray[0].toInteger()*100/360)
    sendEvent(name: "refresh", value: "active")
}
