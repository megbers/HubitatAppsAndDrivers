//Originally Based off https://community.hubitat.com/t/release-tasmota-sonoff-hubitat-driver-device-support/15445/30

metadata {
    definition(name: "Feit Light Bulb", namespace: "MRE", author: "megbers", ocfDeviceType: "oic.d.light") {
        capability "Actuator"
        capability "Bulb"
        capability "Initialize"
        capability "ColorControl"
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
    sendCommand("HsbColor", "$h,$s,$l")
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
