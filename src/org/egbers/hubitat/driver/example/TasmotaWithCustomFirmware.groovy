/**
 *  Copyright 2020 Markus Liljergren
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/* Acknowledgements:
 * Inspired by work done by Eric Maycock (erocm123) and damondins.
 */


/* Default imports */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput


metadata {
    definition (name: "Tasmota - Generic RGB/RGBW Controller/Bulb/Dimmer", namespace: "tasmota", author: "Markus Liljergren", vid: "generic-switch", importURL: "https://raw.githubusercontent.com/markus-li/Hubitat/release/drivers/expanded/tasmota-generic-rgb-rgbw-controller-bulb-dimmer-expanded.groovy") {
        capability "Actuator"
        capability "Light"
        capability "Switch"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "SwitchLevel"

        // Default Capabilities
        capability "Refresh"
        capability "Configuration"


        // Default Attributes
        attribute   "needUpdate", "string"
        //attribute   "uptime", "string"  // This floods the event log!
        attribute   "ip", "string"
        attribute   "ipLink", "string"
        attribute   "module", "string"
        attribute   "templateData", "string"
        attribute   "driverVersion", "string"

        // Default Attributes for Dimmable Lights
        attribute   "wakeup", "string"


        // Default Commands
        command "reboot"

        // Commands for handling RGBW Devices
        command "white"
        command "red"
        command "green"
        command "blue"

        // Commands for handling Tasmota RGBW Devices
        command "modeNext"
        command "modePrevious"
        command "modeSingleColor"
        command "modeCycleUpColors"
        command "modeCycleDownColors"
        command "modeRandomColors"

        // Commands for handling Tasmota Dimmer Devices
        command "modeWakeUp", [[name:"Wake Up Duration*", type: "NUMBER", description: "1..3000 = set wake up duration in seconds"],
                               [name:"Level", type: "NUMBER", description: "1..100 = target dimming level"] ]
    }

    simulator {
    }

    preferences {

        // Default Preferences
        input(name: "runReset", description: "<i>For details and guidance, see the release thread in the <a href=\"https://community.hubitat.com/t/release-tasmota-7-x-firmware-with-hubitat-support/29368\"> Hubitat Forum</a>. For settings marked as ADVANCED, make sure you understand what they do before activating them. If settings are not reflected on the device, press the Configure button in this driver. Also make sure all settings really are saved and correct.<br/>Type RESET and then press 'Save Preferences' to DELETE all Preferences and return to DEFAULTS.</i>", title: "<b>Settings</b>", displayDuringSetup: false, type: "paragraph", element: "paragraph")
        generate_preferences(configuration_model_debug())

        // Default Preferences for Tasmota
        input(name: "ipAddress", type: "string", title: "<b>Device IP Address</b>", description: "<i>Set this as a default fallback for the auto-discovery feature.</i>", displayDuringSetup: true, required: false)
        input(name: "port", type: "number", title: "<b>Device Port</b>", description: "<i>The http Port of the Device (default: 80)</i>", displayDuringSetup: true, required: false, defaultValue: 80)
        input(name: "override", type: "bool", title: "<b>Override IP</b>", description: "<i>Override the automatically discovered IP address and disable auto-discovery.</i>", displayDuringSetup: true, required: false)
        input(name: "telePeriod", type: "string", title: "<b>Update Frequency</b>", description: "<i>Tasmota sensor value update interval, set this to any value between 10 and 3600 seconds. See the Tasmota docs concerning telePeriod for details. This is NOT a poll frequency. Button/switch changes are immediate and are NOT affected by this. This ONLY affects SENSORS and reporting of data such as UPTIME. (default = 300)</i>", displayDuringSetup: true, required: false)
        generate_preferences(configuration_model_tasmota())
        input(name: "disableModuleSelection", type: "bool", title: "<b>Disable Automatically Setting Module and Template</b>", description: "ADVANCED: <i>Disable automatically setting the Module Type and Template in Tasmota. Enable for using custom Module or Template settings directly on the device. With this disabled, you need to set these settings manually on the device.</i>", displayDuringSetup: true, required: false)
        input(name: "moduleNumber", type: "number", title: "<b>Module Number</b>", description: "ADVANCED: <i>Module Number used in Tasmota. If Device Template is set, this value is IGNORED. (default: -1 (use the default for the driver))</i>", displayDuringSetup: true, required: false, defaultValue: -1)
        input(name: "deviceTemplateInput", type: "string", title: "<b>Device Template</b>", description: "ADVANCED: <i>Set this to a Device Template for Tasmota, leave it EMPTY to use the driver default. Set it to 0 to NOT use a Template. NAME can be maximum 14 characters! (Example: {\"NAME\":\"S120\",\"GPIO\":[0,0,0,0,0,21,0,0,0,52,90,0,0],\"FLAG\":0,\"BASE\":18})</i>", displayDuringSetup: true, required: false)
        input(name: "useIPAsID", type: "bool", title: "<b>IP as Network ID</b>", description: "ADVANCED: <i>Not needed under normal circumstances. Setting this when not needed can break updates. This requires the IP to be static or set to not change in your DHCP server. It will force the use of IP as network ID. When in use, set Override IP to true and input the correct Device IP Address. See the release thread in the Hubitat forum for details and guidance.</i>", displayDuringSetup: true, required: false)
    }
}

def getDeviceInfoByName(infoName) {
    // DO NOT EDIT: This is generated from the metadata!
    // TODO: Figure out how to get this from Hubitat instead of generating this?
    deviceInfo = ['name': 'Tasmota - Generic RGB/RGBW Controller/Bulb/Dimmer', 'namespace': 'tasmota', 'author': 'Markus Liljergren', 'vid': 'generic-switch', 'importURL': 'https://raw.githubusercontent.com/markus-li/Hubitat/release/drivers/expanded/tasmota-generic-rgb-rgbw-controller-bulb-dimmer-expanded.groovy']
    return(deviceInfo[infoName])
}


/* RGBW On/Off functions used when only 1 switch/button exists */
def on() {
    logging("on()", 50)
    def cmds = []
    h = null
    s = null
    b = 100
    if(state != null) {
        //h = state.containsKey("hue") ? state.hue : null
        //s = state.containsKey("saturation") ? state.saturation : null
        b = state.containsKey("level") ? state.level : 100
    }
    if(b < 20) b = 20
    if(state.colorMode == "CT") {
        state.level = b
        cmds << setColorTemperature(colorTemperature ? colorTemperature : 3000)
        cmds << setLevel(state.level, 0)
    } else {
        cmds << setHSB(h, s, b)
    }
    cmds << getAction(getCommandString("Power", "On"))
    return cmds
}

def off() {
    logging("off()", 50)
    def cmds = []
    cmds << getAction(getCommandString("Power", "Off"))
    return cmds
}


/* These functions are unique to each driver */
def parse(description) {
    // parse() Generic Tasmota-device header BEGINS here
    //log.debug "Parsing: ${description}"
    def events = []
    def descMap = parseDescriptionAsMap(description)
    def body
    //log.debug "descMap: ${descMap}"

    if (!state.mac || state.mac != descMap["mac"]) {
        logging("Mac address of device found ${descMap["mac"]}",1)
        state.mac = descMap["mac"]
    }

    prepareDNI()

    if (descMap["body"] && descMap["body"] != "T04=") body = new String(descMap["body"].decodeBase64())

    if (body && body != "") {
        if(body.startsWith("{") || body.startsWith("[")) {
            logging("========== Parsing Report ==========",99)
            def slurper = new JsonSlurper()
            def result = slurper.parseText(body)

            logging("result: ${result}",0)
            // parse() Generic header ENDS here


            // Standard Basic Data parsing
            if (result.containsKey("POWER")) {
                logging("POWER: $result.POWER",99)
                events << createEvent(name: "switch", value: result.POWER.toLowerCase())
            }
            if (result.containsKey("StatusNET")) {
                logging("StatusNET: $result.StatusNET",99)
                result << result.StatusNET
                //logging("result: ${result}",0)
            }
            if (result.containsKey("LoadAvg")) {
                logging("LoadAvg: $result.LoadAvg",99)
            }
            if (result.containsKey("Sleep")) {
                logging("Sleep: $result.Sleep",99)
            }
            if (result.containsKey("SleepMode")) {
                logging("SleepMode: $result.SleepMode",99)
            }
            if (result.containsKey("Vcc")) {
                logging("Vcc: $result.Vcc",99)
            }
            if (result.containsKey("Hostname")) {
                logging("Hostname: $result.Hostname",99)
            }
            if (result.containsKey("IPAddress") && (override == false || override == null)) {
                logging("IPAddress: $result.IPAddress",99)
                events << createEvent(name: "ip", value: "$result.IPAddress")
                //logging("ipLink: <a target=\"device\" href=\"http://$result.IPAddress\">$result.IPAddress</a>",10)
                events << createEvent(name: "ipLink", value: "<a target=\"device\" href=\"http://$result.IPAddress\">$result.IPAddress</a>")
            }
            if (result.containsKey("WebServerMode")) {
                logging("WebServerMode: $result.WebServerMode",99)
            }
            if (result.containsKey("Version")) {
                logging("Version: $result.Version",99)
            }
            if (result.containsKey("Module") && !result.containsKey("Version")) {
                // The check for Version is here to avoid using the wrong message
                logging("Module: $result.Module",50)
                events << createEvent(name: "module", value: "$result.Module")
            }
            // When it is a Template, it looks a bit different
            if (result.containsKey("NAME") && result.containsKey("GPIO") && result.containsKey("FLAG") && result.containsKey("BASE")) {
                n = result.toMapString()
                n = n.replaceAll(', ',',')
                n = n.replaceAll('\\[','{').replaceAll('\\]','}')
                n = n.replaceAll('NAME:', '"NAME":"').replaceAll(',GPIO:\\{', '","GPIO":\\[')
                n = n.replaceAll('\\},FLAG', '\\],"FLAG"').replaceAll('BASE', '"BASE"')
                // TODO: Learn how to do this the right way in Groovy
                logging("Template: $n",50)
                events << createEvent(name: "templateData", value: "${n}")
            }
            if (result.containsKey("RestartReason")) {
                logging("RestartReason: $result.RestartReason",99)
            }
            if (result.containsKey("TuyaMCU")) {
                logging("TuyaMCU: $result.TuyaMCU",99)
                events << createEvent(name: "tuyaMCU", value: "$result.TuyaMCU")
            }
            if (result.containsKey("SetOption81")) {
                logging("SetOption81: $result.SetOption81",99)
            }
            if (result.containsKey("SetOption113")) {
                logging("SetOption113 (Hubitat enabled): $result.SetOption113",99)
            }
            if (result.containsKey("Uptime")) {
                logging("Uptime: $result.Uptime",99)
                // Even with "displayed: false, archivable: false" these events still show up under events... There is no way of NOT having it that way...
                //events << createEvent(name: 'uptime', value: result.Uptime, displayed: false, archivable: false)
                state.uptime = result.Uptime
            }

            // Standard Wifi Data parsing
            if (result.containsKey("Wifi")) {
                if (result.Wifi.containsKey("AP")) {
                    logging("AP: $result.Wifi.AP",99)
                }
                if (result.Wifi.containsKey("BSSId")) {
                    logging("BSSId: $result.Wifi.BSSId",99)
                }
                if (result.Wifi.containsKey("Channel")) {
                    logging("Channel: $result.Wifi.Channel",99)
                }
                if (result.Wifi.containsKey("RSSI")) {
                    logging("RSSI: $result.Wifi.RSSI",99)
                }
                if (result.Wifi.containsKey("SSId")) {
                    logging("SSId: $result.Wifi.SSId",99)
                }
            }

            // Standard RGBW Device Data parsing
            if (result.containsKey("HSBColor")) {
                hsbColor = result.HSBColor.tokenize(",")
                hsbColor[0] = Math.round((hsbColor[0] as Integer) / 3.6)
                hsbColor[1] = hsbColor[1] as Integer
                hsbColor[2] = hsbColor[2] as Integer
                logging("hsbColor: ${hsbColor}", 1)
                if(device.currentValue('hue') != hsbColor[0] ) events << createEvent(name: "hue", value: hsbColor[0])
                if(device.currentValue('saturation') != hsbColor[1] ) events << createEvent(name: "saturation", value: hsbColor[1])
            }
            if (result.containsKey("Color")) {
                color = result.Color
                logging("Color: ${color.tokenize(",")}", 1)
            }
            if (result.containsKey("CT")) {
                t = Math.round(1000000/result.CT)
                if(colorTemperature != t ) events << createEvent(name: "colorTemperature", value: t)
                logging("CT: $result.CT ($t)",99)
            }

            // Standard Dimmable Device Data parsing
            if (result.containsKey("Dimmer")) {
                dimmer = result.Dimmer
                logging("Dimmer: ${dimmer}", 1)
                state.level = dimmer
                if(device.currentValue('level') != dimmer ) events << createEvent(name: "level", value: dimmer)
            }
            if (result.containsKey("Wakeup")) {
                wakeup = result.Wakeup
                logging("Wakeup: ${wakeup}", 1)
                events << createEvent(name: "wakeup", value: wakeup)
            }
            // parse() Generic Tasmota-device footer BEGINS here
        } else {
            //log.debug "Response is not JSON: $body"
        }
    }

    if (!device.currentValue("ip") || (device.currentValue("ip") != getDataValue("ip"))) {
        curIP = getDataValue("ip")
        logging("Setting IP: $curIP", 1)
        events << createEvent(name: 'ip', value: curIP)
        events << createEvent(name: "ipLink", value: "<a target=\"device\" href=\"http://$curIP\">$curIP</a>")
    }

    return events
    // parse() Generic footer ENDS here
}

def update_needed_settings() {
    // updateNeededSettings() Generic header BEGINS here
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]

    state.settings = settings

    def configuration = new XmlSlurper().parseText(configuration_model_tasmota())
    def isUpdateNeeded = "NO"

    if(runReset != null && runReset == 'RESET') {
        for ( e in state.settings ) {
            logging("Deleting '${e.key}' with value = ${e.value} from Settings", 50)
            // Not sure which ones are needed, so doing all...
            device.clearSetting("${e.key}")
            device.removeSetting("${e.key}")
            state.settings.remove("${e.key}")
        }
    }

    prepareDNI()

    // updateNeededSettings() Generic header ENDS here


    // Tasmota Module and Template selection command (autogenerated)
    cmds << getAction(getCommandString("Module", null))
    cmds << getAction(getCommandString("Template", null))
    if(disableModuleSelection == null) disableModuleSelection = false
    moduleNumberUsed = moduleNumber
    if(moduleNumber == null || moduleNumber == -1) moduleNumberUsed = -1
    useDefaultTemplate = false
    defaultDeviceTemplate = ''
    if(deviceTemplateInput != null && deviceTemplateInput == "0") {
        useDefaultTemplate = true
        defaultDeviceTemplate = ''
    }
    if(deviceTemplateInput == null || deviceTemplateInput == "") {
        // We should use the default of the driver
        useDefaultTemplate = true
        defaultDeviceTemplate = ''
    }
    if(deviceTemplateInput != null) deviceTemplateInput = deviceTemplateInput.replaceAll(' ','')
    if(disableModuleSelection == false && ((deviceTemplateInput != null && deviceTemplateInput != "") ||
            (useDefaultTemplate && defaultDeviceTemplate != ""))) {
        if(useDefaultTemplate == false && deviceTemplateInput != null && deviceTemplateInput != "") {
            usedDeviceTemplate = deviceTemplateInput
        } else {
            usedDeviceTemplate = defaultDeviceTemplate
        }
        logging("Setting the Template soon...", 10)
        logging("templateData = ${device.currentValue('templateData')}", 10)
        if(usedDeviceTemplate != '') moduleNumberUsed = 0  // This activates the Template when set
        if(usedDeviceTemplate != null && device.currentValue('templateData') != null && device.currentValue('templateData') != usedDeviceTemplate) {
            logging("The template is NOT set to '${usedDeviceTemplate}', it is set to '${device.currentValue('templateData')}'",10)
            urlencodedTemplate = URLEncoder.encode(usedDeviceTemplate).replace("+", "%20")
            // The NAME part of th Device Template can't exceed 14 characters! More than that and they will be truncated.
            // TODO: Parse and limit the size of NAME
            cmds << getAction(getCommandString("Template", "${urlencodedTemplate}"))
        } else if (device.currentValue('module') == null){
            // Update our stored value!
            cmds << getAction(getCommandString("Template", null))
        }else if (usedDeviceTemplate != null) {
            logging("The template is set to '${usedDeviceTemplate}' already!",10)
        }
    } else {
        logging("Can't set the Template...", 10)
        logging(device.currentValue('templateData'), 10)
        //logging("deviceTemplateInput: '${deviceTemplateInput}'", 10)
        //logging("disableModuleSelection: '${disableModuleSelection}'", 10)
    }
    if(disableModuleSelection == false && moduleNumberUsed != null && moduleNumberUsed >= 0) {
        logging("Setting the Module soon...", 10)
        logging("device.currentValue('module'): '${device.currentValue('module')}'", 10)
        if(moduleNumberUsed != null && device.currentValue('module') != null && !device.currentValue('module').startsWith("[${moduleNumberUsed}:")) {
            logging("This DOESN'T start with [${moduleNumberUsed} ${device.currentValue('module')}",10)
            cmds << getAction(getCommandString("Module", "${moduleNumberUsed}"))
        } else if (moduleNumberUsed != null && device.currentValue('module') != null){
            logging("This starts with [${moduleNumberUsed} ${device.currentValue('module')}",10)
        } else if (device.currentValue('module') == null){
            // Update our stored value!
            cmds << getAction(getCommandString("Module", null))
        } else {
            logging("Module is set to '${device.currentValue('module')}', and it's set to be null, report this to the creator of this driver!",10)
        }
    } else {
        logging("Setting the Module has been disabled!", 10)
    }

    // Disabling these here, but leaving them if anyone needs them
    // If another driver has set SetOption81 to 1, the below might be needed, or you can use:
    // http://<device IP>/cm?user=admin&password=<your password>&cmnd=SetOption81%200
    // or without username and password:
    // http://<device IP>/cm?cmnd=SetOption81%200
    //cmds << getAction(getCommandString("SetOption81", "0")) // Set PCF8574 component behavior for all ports as inverted (default=0)
    //cmds << getAction(getCommandString("LedPower", "1"))  // 1 = turn LED ON and set LedState 8
    //cmds << getAction(getCommandString("LedState", "8"))  // 8 = LED on when Wi-Fi and MQTT are connected.

    cmds << getAction(getCommandString("WebLog", "2")) // To avoid errors in the Hubitat logs, make sure this is 2


    // updateNeededSettings() TelePeriod setting
    cmds << getAction(getCommandString("TelePeriod", (telePeriod == '' || telePeriod == null ? "300" : telePeriod)))


    // updateNeededSettings() Generic footer BEGINS here
    cmds << getAction(getCommandString("SetOption113", "1")) // Hubitat Enabled
    // Disabling Emulation so that we don't flood the logs with upnp traffic
    //cmds << getAction(getCommandString("Emulation", "0")) // Emulation Disabled
    cmds << getAction(getCommandString("HubitatHost", device.hub.getDataValue("localIP")))
    logging("HubitatPort: ${device.hub.getDataValue("localSrvPortTCP")}", 1)
    cmds << getAction(getCommandString("HubitatPort", device.hub.getDataValue("localSrvPortTCP")))
    cmds << getAction(getCommandString("FriendlyName1", URLEncoder.encode(device.displayName.take(32)))) // Set to a maximum of 32 characters

    if(override == true) {
        cmds << sync(ipAddress)
    }

    //logging("Cmds: " +cmds,1)
    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: false)
    return cmds
    // updateNeededSettings() Generic footer ENDS here
}

/* Default functions go here */
private def getDriverVersion() {
    logging("getDriverVersion()", 50)
    def cmds = []
    comment = "RGB+WW+CW should all work properly, please report progress"
    if(comment != "") state.comment = comment
    sendEvent(name: "driverVersion", value: "v0.9.3 for Tasmota 7.x/8.x (Hubitat version)")
    return cmds
}


/* Logging function included in all drivers */
private def logging(message, level) {
    if (logLevel != "0"){
        switch (logLevel) {
            case "-1": // Insanely verbose
                if (level >= 0 && level <= 100)
                    log.debug "$message"
                break
            case "1": // Very verbose
                if (level >= 1 && level < 99 || level == 100)
                    log.debug "$message"
                break
            case "10": // A little less
                if (level >= 10 && level < 99 || level == 100)
                    log.debug "$message"
                break
            case "50": // Rather chatty
                if (level >= 50 )
                    log.debug "$message"
                break
            case "99": // Only parsing reports
                if (level >= 99 )
                    log.debug "$message"
                break
        }
    }
}


/* Helper functions included in all drivers */
def installed() {
    logging("installed()", 50)
    configure()
    try {
        // In case we have some more to run specific to this driver
        installedAdditional()
    } catch (MissingMethodException e) {
        // ignore
    }
}

/*
	initialize

	Purpose: initialize the driver
	Note: also called from updated() in most drivers
*/
void initialize()
{
    logging("initialize()", 50)
    unschedule()
    // disable debug logs after 30 min, unless override is in place
    if (logLevel != "0") {
        if(runReset != "DEBUG") {
            log.warn "Debug logging will be disabled in 30 minutes..."
        } else {
            log.warn "Debug logging will NOT BE AUTOMATICALLY DISABLED!"
        }
        runIn(1800, logsOff)
    }
}

def configure() {
    logging("configure()", 50)
    def cmds = []
    cmds = update_needed_settings()
    try {
        // Run the getDriverVersion() command
        newCmds = getDriverVersion()
        if (newCmds != null && newCmds != []) cmds = cmds + newCmds
    } catch (MissingMethodException e) {
        // ignore
    }
    if (cmds != []) cmds
}

def generate_preferences(configuration_model)
{
    def configuration = new XmlSlurper().parseText(configuration_model)

    configuration.Value.each
            {
                if(it.@hidden != "true" && it.@disabled != "true"){
                    switch(it.@type)
                    {
                        case ["number"]:
                            input "${it.@index}", "number",
                                    title:"<b>${it.@label}</b>\n" + "${it.Help}",
                                    description: "<i>${it.@description}</i>",
                                    range: "${it.@min}..${it.@max}",
                                    defaultValue: "${it.@value}",
                                    displayDuringSetup: "${it.@displayDuringSetup}"
                            break
                        case "list":
                            def items = []
                            it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                            input "${it.@index}", "enum",
                                    title:"<b>${it.@label}</b>\n" + "${it.Help}",
                                    description: "<i>${it.@description}</i>",
                                    defaultValue: "${it.@value}",
                                    displayDuringSetup: "${it.@displayDuringSetup}",
                                    options: items
                            break
                        case ["password"]:
                            input "${it.@index}", "password",
                                    title:"<b>${it.@label}</b>\n" + "${it.Help}",
                                    description: "<i>${it.@description}</i>",
                                    displayDuringSetup: "${it.@displayDuringSetup}"
                            break
                        case "decimal":
                            input "${it.@index}", "decimal",
                                    title:"<b>${it.@label}</b>\n" + "${it.Help}",
                                    description: "<i>${it.@description}</i>",
                                    range: "${it.@min}..${it.@max}",
                                    defaultValue: "${it.@value}",
                                    displayDuringSetup: "${it.@displayDuringSetup}"
                            break
                        case "boolean":
                            input "${it.@index}", "boolean",
                                    title:"<b>${it.@label}</b>\n" + "${it.Help}",
                                    description: "<i>${it.@description}</i>",
                                    defaultValue: "${it.@value}",
                                    displayDuringSetup: "${it.@displayDuringSetup}"
                            break
                    }
                }
            }
}

def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]
    currentProperties."${cmd.name}" = cmd.value

    if (state.settings?."${cmd.name}" != null)
    {
        if (state.settings."${cmd.name}".toString() == cmd.value)
        {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: false)
        }
        else
        {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: false)
        }
    }
    state.currentProperties = currentProperties
}

/*
	logsOff

	Purpose: automatically disable debug logging after 30 mins.
	Note: scheduled in Initialize()
*/
void logsOff(){
    if(runReset != "DEBUG") {
        log.warn "Debug logging disabled..."
        // Setting logLevel to "0" doesn't seem to work, it disables logs, but does not update the UI...
        //device.updateSetting("logLevel",[value:"0",type:"string"])
        //app.updateSetting("logLevel",[value:"0",type:"list"])
        // Not sure which ones are needed, so doing all... This works!
        device.clearSetting("logLevel")
        device.removeSetting("logLevel")
        state.settings.remove("logLevel")
    } else {
        log.warn "OVERRIDE: Disabling Debug logging will not execute with 'DEBUG' set..."
        if (logLevel != "0") runIn(1800, logsOff)
    }
}

private def getFilteredDeviceDriverName() {
    deviceDriverName = getDeviceInfoByName('name')
    if(deviceDriverName.toLowerCase().endsWith(' (parent)')) {
        deviceDriverName = deviceDriverName.substring(0, deviceDriverName.length()-9)
    }
    return deviceDriverName
}

private def getFilteredDeviceDisplayName() {
    device_display_name = device.displayName.replace(' (parent)', '').replace(' (Parent)', '')
    return device_display_name
}

def configuration_model_debug()
{
    '''
<configuration>
<Value type="list" index="logLevel" label="Debug Log Level" description="Under normal operations, set this to None. Only needed for debugging. Auto-disabled after 30 minutes." value="0" setting_type="preference" fw="">
<Help>
</Help>
    <Item label="None" value="0" />
    <Item label="Insanely Verbose" value="-1" />
    <Item label="Very Verbose" value="1" />
    <Item label="Verbose" value="10" />
    <Item label="Reports+Status" value="50" />
    <Item label="Reports" value="99" />
    </Value>
</configuration>
'''
}

/* Helper functions included in all Tasmota drivers */
def refresh() {
    logging("refresh()", 10)
    def cmds = []
    cmds << getAction(getCommandString("Status", "0"))
    try {
        // In case we have some more to run specific to this driver
        refreshAdditional()
    } catch (MissingMethodException e) {
        // ignore
    }
    return cmds
}

def reboot() {
    logging("reboot()", 10)
    getAction(getCommandString("Restart", "1"))
}

def updated()
{
    logging("updated()", 10)
    def cmds = []
    cmds = update_needed_settings()
    //sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: false)
    logging(cmds, 0)
    try {
        // Also run initialize(), if it exists...
        initialize()
    } catch (MissingMethodException e) {
        // ignore
    }
    if (cmds != [] && cmds != null) cmds
}

def prepareDNI() {
    if (useIPAsID) {
        hexIPAddress = setDeviceNetworkId(ipAddress, true)
        if(hexIPAddress != null && state.dni != hexIPAddress) {
            state.dni = hexIPAddress
            updateDNI()
        }
    }
    else if (state.mac != null && state.dni != state.mac) {
        state.dni = setDeviceNetworkId(state.mac)
        updateDNI()
    }
}



def getCommandString(command, value) {
    def uri = "/cm?"
    if (password) {
        uri += "user=admin&password=${password}&"
    }
    if (value) {
        uri += "cmnd=${command}%20${value}"
    }
    else {
        uri += "cmnd=${command}"
    }
    return uri
}

def getMultiCommandString(commands) {
    def uri = "/cm?"
    if (password) {
        uri += "user=admin&password=${password}&"
    }
    uri += "cmnd=backlog%20"
    commands.each {cmd->
        if(cmd.containsKey("value")) {
            uri += "${cmd['command']}%20${cmd['value']}%3B%20"
        } else {
            uri += "${cmd['command']}%3B%20"
        }
    }
    return uri
}

def parseDescriptionAsMap(description) {
    description.split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")

        if (nameAndValue.length == 2) map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
        else map += [(nameAndValue[0].trim()):""]
    }
}

private getAction(uri){
    logging("Using getAction for '${uri}'...", 0)
    return httpGetAction(uri)
}

private httpGetAction(uri){
    updateDNI()

    def headers = getHeader()
    //logging("Using httpGetAction for '${uri}'...", 0)
    def hubAction = null
    try {
        hubAction = new hubitat.device.HubAction(
                method: "GET",
                path: uri,
                headers: headers
        )
    } catch (e) {
        log.error "Error in httpGetAction(uri): $e ('$uri')"
    }
    return hubAction
}

private postAction(uri, data){
    updateDNI()

    def headers = getHeader()

    def hubAction = null
    try {
        hubAction = new hubitat.device.HubAction(
                method: "POST",
                path: uri,
                headers: headers,
                body: data
        )
    } catch (e) {
        log.error "Error in postAction(uri, data): $e ('$uri', '$data')"
    }
    return hubAction
}

private onOffCmd(value, endpoint) {
    logging("onOffCmd, value: $value, endpoint: $endpoint", 1)
    def cmds = []
    cmds << getAction(getCommandString("Power$endpoint", "$value"))
    return cmds
}

private setDeviceNetworkId(macOrIP, isIP = false){
    def myDNI
    if (isIP == false) {
        myDNI = macOrIP
    } else {
        logging("About to convert ${macOrIP}...", 0)
        myDNI = convertIPtoHex(macOrIP)
    }
    logging("Device Network Id should be set to ${myDNI} from ${macOrIP}", 0)
    return myDNI
}

private updateDNI() {
    if (state.dni != null && state.dni != "" && device.deviceNetworkId != state.dni) {
        logging("Device Network Id will be set to ${state.dni} from ${device.deviceNetworkId}", 0)
        device.deviceNetworkId = state.dni
    }
}

private getHostAddress() {
    if (port == null) {
        port = 80
    }
    if (override == true && ipAddress != null){
        return "${ipAddress}:${port}"
    }
    else if(getDeviceDataByName("ip") && getDeviceDataByName("port")){
        return "${getDeviceDataByName("ip")}:${getDeviceDataByName("port")}"
    }else{
        return "${ip}:80"
    }
}

private String convertIPtoHex(ipAddress) {
    String hex = null
    if(ipAddress != null) {
        hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
        logging("Get this IP in hex: ${hex}", 0)
    } else {
        hex = null
        if (useIPAsID) {
            logging('ERROR: To use IP as Network ID "Device IP Address" needs to be set and "Override IP" needs to be enabled! If this error persists, consult the release thread in the Hubitat Forum.')
        }
    }
    return hex
}

private String urlEscape(url) {
    return(URLEncoder.encode(url).replace("+", "%20"))
}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}

private encodeCredentials(username, password){
    def userpassascii = "${username}:${password}"
    def userpass = "Basic " + userpassascii.bytes.encodeBase64().toString()
    return userpass
}

private getHeader(userpass = null){
    def headers = [:]
    headers.put("Host", getHostAddress())
    headers.put("Content-Type", "application/x-www-form-urlencoded")
    if (userpass != null)
        headers.put("Authorization", userpass)
    return headers
}

def sync(ip, port = null) {
    def existingIp = getDataValue("ip")
    def existingPort = getDataValue("port")
    logging("Running sync()", 1)
    if (ip && ip != existingIp) {
        updateDataValue("ip", ip)
        sendEvent(name: 'ip', value: ip)
        sendEvent(name: "ipLink", value: "<a target=\"device\" href=\"http://$ip\">$ip</a>")
        logging("IP set to ${ip}", 1)
    }
    if (port && port != existingPort) {
        updateDataValue("port", port)
        logging("Port set to ${port}", 1)
    }
}

def configuration_model_tasmota()
{
    '''
<configuration>
<Value type="password" byteSize="1" index="password" label="Device Password" description="REQUIRED if set on the Device! Otherwise leave empty." min="" max="" value="" setting_type="preference" fw="">
<Help>
</Help>
</Value>
</configuration>
'''
}

/* Helper functions included in all drivers using RGB, RGBW or Dimmers */
def setColor(value) {
    logging("setColor('${value}')", 10)
    if (value != null && value instanceof Map) {
        def h = value.containsKey("hue") ? value.hue : 0
        def s = value.containsKey("saturation") ? value.saturation : 0
        def b = value.containsKey("level") ? value.level : 0
        setHSB(h, s, b)
    } else {
        logging("setColor('${value}') called with an INVALID argument!", 10)
    }
}

def setHue(h) {
    logging("setHue('${h}')", 10)
    return(setHSB(h, null, null))
}

def setSaturation(s) {
    logging("setSaturation('${s}')", 10)
    return(setHSB(null, s, null))
}

def setLevel(b) {
    logging("setLevel('${b}')", 10)
    //return(setHSB(null, null, b))
    return(setLevel(b, 0))
}

def rgbToHSB(red, green, blue) {
    // All credits for this function goes to Joe Julian (joejulian):
    // https://gist.github.com/joejulian/970fcd5ecf3b792bc78a6d6ebc59a55f
    float r = red / 255f
    float g = green / 255f
    float b = blue / 255f
    float max = [r, g, b].max()
    float min = [r, g, b].min()
    float delta = max - min
    def hue = 0
    def saturation = 0
    if (max == min) {
        hue = 0
    } else if (max == r) {
        def h1 = (g - b) / delta / 6
        def h2 = h1.asType(int)
        if (h1 < 0) {
            hue = (360 * (1 + h1 - h2)).round()
        } else {
            hue = (360 * (h1 - h2)).round()
        }
        logging("rgbToHSB: red max=${max} min=${min} delta=${delta} h1=${h1} h2=${h2} hue=${hue}", 1)
    } else if (max == g) {
        hue = 60 * ((b - r) / delta + 2)
        logging("rgbToHSB: green hue=${hue}", 1)
    } else {
        hue = 60 * ((r - g) / (max - min) + 4)
        logging("rgbToHSB: blue hue=${hue}", 1)
    }

    // Convert hue to Hubitat value:
    hue = Math.round((hue) / 3.6)

    if (max == 0) {
        saturation = 0
    } else {
        saturation = delta / max * 100
    }

    def level = max * 100

    return [
            "hue": hue.asType(int),
            "saturation": saturation.asType(int),
            "level": level.asType(int),
    ]
}

// Fixed colours
def white() {
    logging("white()", 10)
    // This is separated to be able to reuse functions between platforms
    return(whiteForPlatform())
}

def red() {
    logging("red()", 10)
    return(setRGB(255, 0, 0))
}

def green() {
    logging("green()", 10)
    return(setRGB(0, 255, 0))
}

def blue() {
    logging("blue()", 10)
    return(setRGB(0, 0, 255))
}

def yellow() {
    logging("yellow()", 10)
    return(setRGB(255, 255, 0))
}

def lightBlue() {
    logging("lightBlue()", 10)
    return(setRGB(0, 255, 255))
}

def pink() {
    logging("pink()", 10)
    return(setRGB(255, 0, 255))
}

/* Helper functions included in all Tasmota drivers using RGB, RGBW or Dimmers */
def setColorTemperature(value) {
    logging("setColorTemperature('${value}')", 10)
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
    logging("setColorTemperature('${t}') ADJUSTED to Mired", 10)
    getAction(getCommandString("CT", "${t}"))
}

def setHSB(h, s, b) {
    logging("setHSB('${h}','${s}','${b}')", 10)
    return(setHSB(h, s, b, true))
}

def setHSB(h, s, b, callWhite) {
    logging("setHSB('${h}','${s}','${b}', callWhite=${String.valueOf(callWhite)})", 10)
    adjusted = False
    if(h == null || h == 'NaN') {
        h = state != null && state.containsKey("hue") ? state.hue : 0
        adjusted = True
    }
    if(s == null || s == 'NaN') {
        s = state != null && state.containsKey("saturation") ? state.saturation : 0
        adjusted = True
    }
    if(b == null || b == 'NaN') {
        b = state != null && state.containsKey("level") ? state.level : 0
        adjusted = True
    }
    if(adjusted) {
        logging("ADJUSTED setHSB('${h}','${s}','${b}'", 1)
    }
    adjustedH = Math.round(h*3.6)
    if( adjustedH > 360 ) { adjustedH = 360 }
    if( b < 0 ) b = 0
    if( b > 100 ) b = 100
    hsbcmd = "${adjustedH},${s},${b}"
    logging("hsbcmd = ${hsbcmd}", 1)
    state.hue = h
    state.saturation = s
    state.level = b
    state.colorMode = "RGB"
    if (hsbcmd.startsWith("0,0,")) {
        //state.colorMode = "white"
        //sendEvent(name: "colorMode", value: "CT")
        return(white())
        //return(getAction(getCommandString("hsbcolor", hsbcmd)))
    } else {
        if(colorMode != "RGB" ) sendEvent(name: "colorMode", value: "RGB")
        return(getAction(getCommandString("HsbColor", hsbcmd)))
    }
}

def setRGB(r,g,b) {
    logging("setRGB('${r}','${g}','${b}')", 10)
    adjusted = False
    if(r == null || r == 'NaN') {
        r = 0
        adjusted = True
    }
    if(g == null || g == 'NaN') {
        g = 0
        adjusted = True
    }
    if(b == null || b == 'NaN') {
        b = 0
        adjusted = True
    }
    if(adjusted) {
        logging("ADJUSTED setRGB('${r}','${g}','${b}')", 1)
    }
    rgbcmd = "${r},${g},${b}"
    logging("rgbcmd = ${rgbcmd}", 1)
    state.red = r
    state.green = g
    state.blue = b
    // Calculate from RGB values
    hsbColor = rgbToHSB(r, g, b)
    logging("hsbColor from RGB: ${hsbColor}", 1)
    state.colorMode = "RGB"
    //if (hsbcmd == "${hsbColor[0]},${hsbColor[1]},${hsbColor[2]}") state.colorMode = "white"
    state.hue = hsbColor['hue']
    state.saturation = hsbColor['saturation']
    state.level = hsbColor['level']

    return(getAction(getCommandString("Color1", rgbcmd)))
}

def setLevel(l, duration) {
    if (duration == 0) {
        if (state.colorMode == "RGB") {
            return(setHSB(null, null, l))
        } else {
            state.level = l
            return(getAction(getCommandString("Dimmer", "${l}")))
        }
    }
    else if (duration > 0) {
        if (state.colorMode == "RGB") {
            return(setHSB(null, null, l))
        } else {
            if (duration > 10) {duration = 10}
            delay = duration * 10
            fadeCommand = "Fade 1;Speed ${duration};Dimmer ${l};Delay ${delay};Fade 0"
            logging("fadeCommand: '" + fadeCommand + "'", 1)
            return(getAction(getCommandString("Backlog", urlEscape(fadeCommand))))
        }
    }
}

def whiteForPlatform() {
    logging("whiteForPlatform()", 10)
    l = state.level
    //state.colorMode = "white"
    if (l < 10) l = 10
    l = Math.round(l * 2.55).toInteger()
    if (l > 255) l = 255
    lHex = l.toHexString(l)
    hexCmd = "#${lHex}${lHex}${lHex}${lHex}${lHex}"
    logging("hexCmd='${hexCmd}'", 1)
    state.hue = 0
    state.saturation = 0
    state.red = l
    state.green = l
    state.blue = l
    return(getAction(getCommandString("Color1", hexCmd)))
}

// Functions to set RGBW Mode
def modeSet(mode) {
    logging("modeSet('${mode}')", 10)
    getAction(getCommandString("Scheme", "${mode}"))
}

def modeNext() {
    logging("modeNext()", 10)
    if (state.mode < 4) {
        state.mode = state.mode + 1
    } else {
        state.mode = 0
    }
    modeSet(state.mode)
}

def modePrevious() {
    if (state.mode > 0) {
        state.mode = state.mode - 1
    } else {
        state.mode = 4
    }
    modeSet(state.mode)
}

def modeSingleColor() {
    state.mode = 0
    modeSet(state.mode)
}

def modeWakeUp() {
    logging("modeWakeUp()", 1)
    state.mode = 1
    modeSet(state.mode)
}

def modeWakeUp(wakeUpDuration) {
    level = device.currentValue('level')
    nlevel = level > 10 ? level : 10
    logging("modeWakeUp(wakeUpDuration ${wakeUpDuration}, current level: ${nlevel})", 1)
    modeWakeUp(wakeUpDuration, nlevel)
}

def modeWakeUp(wakeUpDuration, level) {
    logging("modeWakeUp(wakeUpDuration ${wakeUpDuration}, level: ${level})", 1)
    state.mode = 1
    wakeUpDuration = wakeUpDuration < 1 ? 1 : wakeUpDuration > 3000 ? 3000 : wakeUpDuration
    level = level < 1 ? 1 : level > 100 ? 100 : level
    state.level = level
    getAction(getMultiCommandString([[command: "WakeupDuration", value: "${wakeUpDuration}"],
                                     [command: "Wakeup", value: "${level}"]]))
}

def modeCycleUpColors() {
    state.mode = 2
    modeSet(state.mode)
}

def modeCycleDownColors() {
    state.mode = 3
    modeSet(state.mode)
}

def modeRandomColors() {
    state.mode = 4
    modeSet(state.mode)
}
