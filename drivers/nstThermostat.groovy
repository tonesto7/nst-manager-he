/**
 *  Nest Thermostat
 *	Copyright (C) 2018, 2019 Anthony Santilli.
 *	Author: Anthony Santilli (@tonesto7), Eric Schott (@imnotbob)
 *  Modified: 04/17/2019
 */

import java.text.SimpleDateFormat
import groovy.time.*

def devVer() { return "2.0.2" }
metadata {
	definition (name: "Nest Thermostat", namespace: "tonesto7", author: "Anthony S.", importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/drivers/nstThermostat.groovy") {
		capability "Actuator"
		capability "Relative Humidity Measurement"
		capability "Refresh"
		capability "Sensor"
		capability "Thermostat"
		capability "Temperature Measurement"

		command "refresh"
		command "poll"

		command "away"
		command "present"
		command "eco"
		//command "setAway"
		//command "setHome"
		command "setPresence"
		//command "setThermostatMode"
		command "levelUpDown"
		command "levelUp"
		command "levelDown"
		command "heatingSetpointUp"
		command "heatingSetpointDown"
		command "coolingSetpointUp"
		command "coolingSetpointDown"
		//command "changeMode"
		//command "changeFanMode"

		//command "ecoDesc", ["string"]
		//command "setNestEta", ["string", "string", "string"]
		//command "cancelNestEta", ["string"]

		attribute "etaBegin", "string"
		attribute "temperatureUnit", "string"
		attribute "targetTemp", "string"
		attribute "softwareVer", "string"
		attribute "lastConnection", "string"
		attribute "apiStatus", "string"
		attribute "hasLeaf", "string"
		attribute "tempLockOn", "string"
		attribute "lockedTempMin", "string"
		attribute "lockedTempMax", "string"
		attribute "onlineStatus", "string"
		attribute "nestPresence", "string"
		attribute "nestThermostatMode", "string"
		attribute "previousthermostatMode", "string"
		attribute "supportedNestThermostatModes", "JSON_OBJECT"
		attribute "nestThermostatOperatingState", "string"
		attribute "presence", "string"
		attribute "canHeat", "string"
		attribute "canCool", "string"
		attribute "hasAuto", "string"
		attribute "hasFan", "string"
		attribute "sunlightCorrectionEnabled", "string"
		attribute "sunlightCorrectionActive", "string"
		attribute "timeToTarget", "string"
		attribute "pauseUpdates", "string"
		attribute "nestType", "string"

		//attribute "coolingSetpoint", "string"
		attribute "coolingSetpointMin", "string"
		attribute "coolingSetpointMax", "string"
		//attribute "heatingSetpoint", "string"
		attribute "heatingSetpointMin", "string"
		attribute "heatingSetpointMax", "string"
		//attribute "thermostatSetpoint", "string"
		attribute "thermostatSetpointMin", "string"
		attribute "thermostatSetpointMax", "string"
	}
	preferences {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
	}
}

def logsOff(){
	log.warn "${device?.displayName} debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def compileForC() {
	return false
}

private def virtType()          { return state?.virtual == true ? true : false }

def lowRange() { return compileForC() ? 9 : 50 }
def highRange() { return compileForC() ? 32 : 90 }
def getRange() { return "${lowRange()}..${highRange()}" }

def initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	verifyDataAttr()
	if(!state?.updatedLastRanAt || now() >= state?.updatedLastRanAt + 2000) {
		state?.updatedLastRanAt = now()
		checkVirtualStatus()
		state?.isInstalled = true
		log.warn "debug logging is: ${logEnable} | description logging is: ${txtEnable}"
		if (logEnable) runIn(1800,logsOff)
	} else {
		log.trace "initialize(): Ran within last 2 seconds - SKIPPING"
	}
        state.remove("enRemDiagLogging")
}

def verifyDataAttr() {
	if(!device?.getDataValue("manufacturer")) {
		updateDataValue("manufacturer", "Nest")
	}
	if(!device?.getDataValue("model")) {
		updateDataValue("model", device?.name as String)
	}
}

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	runIn( 5, "initialize", [overwrite: true] )
}

void uninstalled() {
	log.trace "Device Removed: (${device?.displayName})..."
}

void updated() {
	log.trace "Device Updated: (${device?.displayName})..."
	log.warn "debug logging is: ${logEnable} | description logging is: ${txtEnable}"
	runIn( 5, "initialize", [overwrite: true] )
}

void checkVirtualStatus() {
	if(getDataValue("isVirtual") == null && state?.virtual != null) {
		def res = (state?.virtual instanceof Boolean) ? state?.virtual : false
		Logger("Updating the device's 'isVirtual' data value to (${res})")
		updateDataValue("isVirtual", "${res}")
	} else {
		def dVal = getDataValue("isVirtual").toString() == "true" ? true : false
		if(dVal != state?.virtual || state?.virtual == null) {
			state?.virtual = dVal
			Logger("Setting virtual to ${dVal?.toString()?.toUpperCase()}")
		}
	}
}

def stateRemove(key) {
        state.remove(key?.toString())
        return true
}

def parse(String description) {
	if(txtEnable) { Logger("Parsing '${description}'") }
}

void poll() {
	//Logger("Polling parent...")
	refresh()
}

void refresh() {
	pauseEvent("false")
	parent?.refresh(this)
}

def generateEvent(eventData) {
	def pauseUpd = !device.currentState("pauseUpdates") ? false : device.currentState("pauseUpdates")?.value?.toString()
	if(pauseUpd == "true") { Logger("Changes Paused! Device Command in Progress", "warn"); return; }

	//Logger("processEvent Parsing data ${eventData}", "trace")
	try {
		if(eventData) {
			if(eventData?.virt) { state?.virtual = eventData?.virt }
			if(virtType()) { nestTypeEvent("virtual") } else { nestTypeEvent("physical") }
			state?.childWaitVal = eventData?.childWaitVal.toInteger()
			state?.nestTimeZone = eventData.tz ?: null
			tempUnitEvent(getTemperatureScale())
			if(eventData?.data?.is_locked != null) { tempLockOnEvent(eventData?.data?.is_locked.toString() == "true" ? true : false) }
			canHeatCool(eventData?.data?.can_heat, eventData?.data?.can_cool)
			hasFan(eventData?.data?.has_fan.toString())
			presenceEvent(eventData?.pres)
			etaEvent(eventData?.etaBegin)

			def curMode = device?.currentState("nestThermostatMode")?.value?.toString()
			hvacModeEvent(eventData?.data?.hvac_mode.toString())
			def newMode = device?.currentState("nestThermostatMode")?.value?.toString()

			hvacPreviousModeEvent(eventData?.data?.previous_hvac_mode.toString())
			hasLeafEvent(eventData?.data?.has_leaf)
			humidityEvent(eventData?.data?.humidity.toString())

			nestoperatingStateEvent(eventData?.data?.hvac_state.toString())
			fanModeEvent(eventData?.data?.fan_timer_active.toString())
			operatingStateEvent(eventData?.data?.hvac_state.toString())

			if(!eventData?.data?.last_connection) { lastCheckinEvent(null,null) }
			else { lastCheckinEvent(eventData?.data?.last_connection, eventData?.data?.is_online.toString()) }
			sunlightCorrectionEnabledEvent(eventData?.data?.sunlight_correction_enabled)
			sunlightCorrectionActiveEvent(eventData?.data?.sunlight_correction_active)
			timeToTargetEvent(eventData?.data?.time_to_target, eventData?.data?.time_to_target_training)
			softwareVerEvent(eventData?.data?.software_version.toString())
			//onlineStatusEvent(eventData?.data?.is_online.toString())
			apiStatusEvent(eventData?.apiIssues)
			// safetyTempsEvent(eventData?.safetyTemps)
			// comfortHumidityEvent(eventData?.comfortHumidity)
			// comfortDewpointEvent(eventData?.comfortDewpoint)

			def hvacMode = state?.nestHvac_mode
			def tempUnit = state?.tempUnit
			switch (tempUnit) {
				case "C":
					if(eventData?.data?.locked_temp_min_c && eventData?.data?.locked_temp_max_c) { lockedTempEvent(eventData?.data?.locked_temp_min_c, eventData?.data?.locked_temp_max_c) }
					def temp = eventData?.data?.ambient_temperature_c.toDouble()
					temperatureEvent(temp)

					def heatingSetpoint = 0.0
					def coolingSetpoint = 0.0
					def targetTemp = eventData?.data?.target_temperature_c.toDouble()

					if(hvacMode == "cool") {
						coolingSetpoint = targetTemp
					}
					else if(hvacMode == "heat") {
						heatingSetpoint = targetTemp
					}
					else if(hvacMode == "auto") {
						coolingSetpoint = Math.round(eventData?.data?.target_temperature_high_c.toDouble())
						heatingSetpoint = Math.round(eventData?.data?.target_temperature_low_c.toDouble())
					}
					if(hvacMode == "eco") {
						if(eventData?.data?.eco_temperature_high_c) { coolingSetpoint = eventData?.data?.eco_temperature_high_c.toDouble() }
						else if(eventData?.data?.away_temperature_high_c) { coolingSetpoint = eventData?.data?.away_temperature_high_c.toDouble() }
						if(eventData?.data?.eco_temperature_low_c) { heatingSetpoint = eventData?.data?.eco_temperature_low_c.toDouble() }
						else if(eventData?.data?.away_temperature_low_c) { heatingSetpoint = eventData?.data?.away_temperature_low_c.toDouble() }
					}

					if(hvacMode in ["cool", "auto", "eco"] && state?.can_cool) {
						coolingSetpointEvent(coolingSetpoint)
						if(hvacMode == "eco" && state?.has_auto == false) { targetTemp = coolingSetpoint }
					} else {
						clearCoolingSetpoint()
					}
					if(hvacMode in ["heat", "auto", "eco"] && state?.can_heat) {
						heatingSetpointEvent(heatingSetpoint)
						if(hvacMode == "eco" && state?.has_auto == false) { targetTemp = heatingSetpoint }
					} else {
						clearHeatingSetpoint()
					}

					if(hvacMode in ["cool", "heat"] || (hvacMode == "eco" && state?.has_auto == false)) {
						thermostatSetpointEvent(targetTemp)
					} else {
						sendEvent(name:'thermostatSetpoint', value: "",  descriptionText: "Clear Thermostat Setpoint", displayed: true)
						sendEvent(name:'thermostatSetpointMin', value: "",  descriptionText: "Clear Thermostat SetpointMin", displayed: false)
						sendEvent(name:'thermostatSetpointMax', value: "",  descriptionText: "Clear Thermostat SetpointMax", displayed: false)
					}
					break

				case "F":
					if(eventData?.data?.locked_temp_min_f && eventData?.data?.locked_temp_max_f) { lockedTempEvent(eventData?.data?.locked_temp_min_f, eventData?.data?.locked_temp_max_f) }
					def temp = eventData?.data?.ambient_temperature_f
					temperatureEvent(temp)

					def heatingSetpoint = 0
					def coolingSetpoint = 0
					def targetTemp = eventData?.data?.target_temperature_f

					if(hvacMode == "cool") {
						coolingSetpoint = targetTemp
					}
					else if(hvacMode == "heat") {
						heatingSetpoint = targetTemp
					}
					else if(hvacMode == "auto") {
						coolingSetpoint = eventData?.data?.target_temperature_high_f
						heatingSetpoint = eventData?.data?.target_temperature_low_f
					}
					else if(hvacMode == "eco") {
						if(eventData?.data?.eco_temperature_high_f) { coolingSetpoint = eventData?.data?.eco_temperature_high_f }
						else if(eventData?.data?.away_temperature_high_f) { coolingSetpoint = eventData?.data?.away_temperature_high_f }
						if(eventData?.data?.eco_temperature_low_f)  { heatingSetpoint = eventData?.data?.eco_temperature_low_f }
						else if(eventData?.data?.away_temperature_low_f)  { heatingSetpoint = eventData?.data?.away_temperature_low_f }
					}

					if(hvacMode in ["cool", "auto", "eco"] && state?.can_cool) {
						coolingSetpointEvent(coolingSetpoint)
						if(hvacMode == "eco" && state?.has_auto == false) { targetTemp = coolingSetpoint }
					} else {
						clearCoolingSetpoint()
					}
					if(hvacMode in ["heat", "auto", "eco"] && state?.can_heat) {
						heatingSetpointEvent(heatingSetpoint)
						if(hvacMode == "eco" && state?.has_auto == false) { targetTemp = heatingSetpoint }
					} else {
						clearHeatingSetpoint()
					}
					if(hvacMode in ["cool", "heat"] || (hvacMode == "eco" && state?.has_auto == false)) {
						thermostatSetpointEvent(targetTemp)
					} else {
						sendEvent(name:'thermostatSetpoint', value: "",  descriptionText: "Clear Thermostat Setpoint", displayed: true)
						sendEvent(name:'thermostatSetpointMin', value: "",  descriptionText: "Clear Thermostat SetpointMin", displayed: false)
						sendEvent(name:'thermostatSetpointMax', value: "",  descriptionText: "Clear Thermostat SetpointMax", displayed: false)
					}
					break

				default:
					Logger("no Temperature data $tempUnit")
					break
			}
			lastUpdatedEvent(false)
		}
		return null
	}
	catch (ex) {
		log.error "processEvent Exception: ${ex?.message}"
	}
}

def getStateSize()	{ return state?.toString().length() }
def getStateSizePerc()  { return (int) ((stateSize/100000)*100).toDouble().round(0) }

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDeviceStateData() {
	return getState()
}

def getTimeZone() {
	def tz = null
	if(location?.timeZone) { tz = location?.timeZone }
	else { tz = state?.nestTimeZone ? TimeZone.getTimeZone(state?.nestTimeZone) : null }
	if(!tz) { Logger("getTimeZone: Hub or Nest TimeZone is not found ...", "warn") }
	return tz
}

def tUnitStr() {
	return "\u00b0${state?.tempUnit}"
}

void ecoDesc(val) { }

def pauseEvent(val = false) {
	def curData = device.currentState("pauseUpdates")?.value
	if(isStateChange(device, "pauseUpdates", val.toString())) {
		Logger("Pause Updates is: (${val}) | Previous State: (${curData})")
		sendEvent(name: 'pauseUpdates', value: val, displayed: false)
	}
}

def nestTypeEvent(type) {
	def val = device.currentState("nestType")?.value
	state?.nestType=type
	if(!val.equals(type)) {
		Logger("UPDATED | nestType: (${type}) | Original State: (${val})")
		sendEvent(name: 'nestType', value: type, displayed: true)
	}
}

def sunlightCorrectionEnabledEvent(sunEn) {
	def val = device.currentState("sunlightCorrectionEnabled")?.value
	def newVal = sunEn.toString() == "true" ? true : false
	if(isStateChange(device, "sunlightCorrectionEnabled", newVal.toString())) {
		Logger("SunLight Correction Enabled: (${newVal}) | Previous State: (${val.toString().capitalize()})")
		sendEvent(name: 'sunlightCorrectionEnabled', value: newVal, displayed: false)
	}
}

def sunlightCorrectionActiveEvent(sunAct) {
	def val = device.currentState("sunlightCorrectionActive")?.value
	def newVal = sunAct.toString() == "true" ? true : false
	if(isStateChange(device, "sunlightCorrectionActive", newVal.toString())) {
		Logger("SunLight Correction Active: (${newVal}) | Previous State: (${val.toString().capitalize()})")
		sendEvent(name: 'sunlightCorrectionActive', value: newVal, displayed: false)
	}
}

def timeToTargetEvent(ttt, tttTr) {
	//log.debug "timeToTargetEvent($ttt, $tttTr)"
	def val = device.currentState("timeToTarget")?.value?.toString()
	def opIdle = device.currentState("nestThermostatOperatingState")?.value?.toString() == "off" ? true : false
	//log.debug "opIdle: $opIdle"
	def nVal
	if(ttt) {
		nVal = ttt.toString().replaceAll("\\~", "").toString()
		nVal = nVal.toString().replaceAll("\\>", "").toString()
		nVal = nVal.toString().replaceAll("\\<", "").toInteger()
	}
	def trStr
	if(tttTr) {
		trStr = tttTr.toString() == "training" ? "\n(Still Training)" : ""
	}
	def newVal = ttt ? (nVal == 0 || opIdle ? "System is Idle" : "${nVal} Minutes${trStr}") : "Not Available"
	if(isStateChange(device, "timeToTarget", newVal.toString())) {
		Logger("Time to Target: (${newVal}) | Previous State: (${val.toString().capitalize()})")
		sendEvent(name: 'timeToTarget', value: newVal, displayed: false)
	}
}

def lastCheckinEvent(checkin, isOnline) {
	//def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	//tf.setTimeZone(getTimeZone())
	def regex1 = /Z/
	def t0 = checkin.replaceAll(regex1, "-0000")

	def prevOnlineStat = device.currentState("onlineStatus")?.value

	//def curConn = t0 ? "${tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))}" : "Not Available"
	def curConnFmt = t0 ? "${formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))}" : "Not Available"
	//def curConnSeconds = (t0 && curConnFmt != "Not Available") ? getTimeDiffSeconds(curConnFmt) : 3000

	def onlineStat = isOnline.toString() == "true" ? "online" : "offline"

	if(isStateChange(device, "lastConnection", curConnFmt.toString())) {
		//def lastChk = device.currentState("lastConnection")?.value
		//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000
		// Logger("Last Nest Check-in was: (${curConnFmt}) | Previous Check-in: (${lastChk})")
		sendEvent(name: 'lastConnection', value: curConnFmt?.toString(), isStateChange: true)
	}
	if(isStateChange(device, "onlineStatus", onlineStat?.toString())) {
		Logger("Online Status is: (${onlineStat}) | Previous State: (${prevOnlineStat})")
		sendEvent(name: "onlineStatus", value: onlineStat, descriptionText: "Online Status is: ${onlineStat}", displayed: true, isStateChange: true, state: onlineStat)
	}
}

def lastUpdatedEvent(sendEvt=false) {
	//def now = new Date()
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	//tf.setTimeZone(getTimeZone())
	def lastDt = getDtNow()
	state?.lastUpdatedDt = lastDt?.toString()
	//state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		//def lastUpd = device.currentState("lastUpdatedDt")?.value
		// Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastUpdatedDt', value: lastDt?.toString(), displayed: false, isStateChange: true)
	}
}

def softwareVerEvent(ver) {
	def verVal = device.currentState("softwareVer")?.value
	if(isStateChange(device, "softwareVer", ver.toString())) {
		Logger("Firmware Version: (${ver}) | Previous State: (${verVal})")
		sendEvent(name: 'softwareVer', value: ver, descriptionText: "Firmware Version is now ${ver}", displayed: false, isStateChange: true)
	}
}

def tempUnitEvent(unit) {
	def tmpUnit = device.currentState("temperatureUnit")?.value
	state?.tempUnit = unit
	if(isStateChange(device, "temperatureUnit", unit.toString())) {
		Logger("Temperature Unit: (${unit}) | Previous State: (${tmpUnit})")
		sendEvent(name:'temperatureUnit', value: unit, descriptionText: "Temperature Unit is now: '${unit}'", displayed: true, isStateChange: true)
	}
}

// TODO NOT USED
def targetTempEvent(Double targetTemp) {
	def temp = device.currentState("targetTemperature")?.value?.toString()
	def rTargetTemp = wantMetric() ? targetTemp.round(1) : targetTemp.round(0).toInteger()
	if(isStateChange(device, "targetTemperature", rTargetTemp.toString())) {
		Logger("targetTemperature is (${rTargetTemp}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name:'targetTemperature', value: rTargetTemp, unit: state?.tempUnit, descriptionText: "Target Temperature is ${rTargetTemp}${tUnitStr()}", displayed: false, isStateChange: true)
	}
}

def thermostatSetpointEvent(Double targetTemp) {
	def temp = device.currentState("thermostatSetpoint")?.value?.toString()
	def rTargetTemp = wantMetric() ? targetTemp.round(1) : targetTemp.round(0).toInteger()
	//if(isStateChange(device, "thermostatSetPoint", rTargetTemp.toString())) {
	if(!temp.equals(rTargetTemp.toString())) {
		Logger("thermostatSetPoint Temperature is (${rTargetTemp}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name:'thermostatSetpoint', value: rTargetTemp, unit: state?.tempUnit, descriptionText: "thermostatSetpoint Temperature is ${rTargetTemp}${tUnitStr()}", displayed: false, isStateChange: true)
	}

	def curMinTemp
	def curMaxTemp = 100.0
	def locked = state?.tempLockOn.toBoolean()
	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	if(wantMetric()) {
		if(curMinTemp < 9.0) { curMinTemp = 9.0 }
		if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
	} else {
		if(curMinTemp < 50) { curMinTemp = 50 }
		if(curMaxTemp > 90) { curMaxTemp = 90 }
	}
	sendEvent(name:'thermostatSetpointMin', value: curMinTemp, unit: state?.tempUnit, descriptionText: "Thermostat SetpointMin is ${curMinTemp}${tUnitStr()}", state: "cool")
	sendEvent(name:'thermostatSetpointMax', value: curMaxTemp, unit: state?.tempUnit, descriptionText: "Thermostat SetpointMax is ${curMaxTemp}${tUnitStr()}", state: "cool")
}

def temperatureEvent(Double tempVal) {
	def temp = device.currentState("temperature")?.value?.toString()
	def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(isStateChange(device, "temperature", rTempVal.toString())) {
		Logger("Temperature is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name:'temperature', value: rTempVal, unit: state?.tempUnit, descriptionText: "Ambient Temperature is ${rTempVal}${tUnitStr()}", displayed: true, isStateChange: true)
	}
	// checkSafetyTemps()
}

def heatingSetpointEvent(Double tempVal) {
	def temp = device.currentState("heatingSetpoint")?.value?.toString()
	if(tempVal.toInteger() == 0 || !state?.can_heat || (getHvacMode == "off")) {
		if(temp != "") { clearHeatingSetpoint() }
	} else {
		def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
		if(isStateChange(device, "heatingSetpoint", rTempVal.toString())) {
			Logger("Heat Setpoint is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
			def disp = false
			def hvacMode = getHvacMode()
			if(hvacMode in ["auto", "heat"]) { disp = true }
			sendEvent(name:'heatingSetpoint', value: rTempVal, unit: state?.tempUnit, descriptionText: "Heat Setpoint is ${rTempVal}${tUnitStr()}", displayed: disp, isStateChange: true, state: "heat")
//			state?.allowHeat = true
		}

		def curMinTemp
		def curMaxTemp = 100.0
		def locked = state?.tempLockOn.toBoolean()
		if(locked) {
			curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
			curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
		}
		if(wantMetric()) {
			if(curMinTemp < 9.0) { curMinTemp = 9.0 }
			if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
		} else {
			if(curMinTemp < 50) { curMinTemp = 50 }
			if(curMaxTemp > 90) { curMaxTemp = 90 }
		}
		sendEvent(name:'heatingSetpointMin', value: curMinTemp, unit: state?.tempUnit, descriptionText: "Heat SetpointMin is ${curMinTemp}${tUnitStr()}", state: "heat")
		sendEvent(name:'heatingSetpointMax', value: curMaxTemp, unit: state?.tempUnit, descriptionText: "Heat SetpointMax is ${curMaxTemp}${tUnitStr()}", state: "heat")
	}
}

def coolingSetpointEvent(Double tempVal) {
	def temp = device.currentState("coolingSetpoint")?.value?.toString()
	if(tempVal.toInteger() == 0 || !state?.can_cool || (getHvacMode == "off")) {
		if(temp != "") { clearCoolingSetpoint() }
	} else {
		def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
		if(isStateChange(device, "coolingSetpoint", rTempVal.toString())) {
			Logger("Cool Setpoint is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
			def disp = false
			def hvacMode = getHvacMode()
			if(hvacMode in ["auto", "cool"]) { disp = true }
			sendEvent(name:'coolingSetpoint', value: rTempVal, unit: state?.tempUnit, descriptionText: "Cool Setpoint is ${rTempVal}${tUnitStr()}", displayed: disp, isStateChange: true, state: "cool")
//			state?.allowCool = true
		}

		def curMinTemp
		def curMaxTemp = 100.0
		def locked = state?.tempLockOn.toBoolean()
		if(locked) {
			curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
			curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
		}
		if(wantMetric()) {
			if(curMinTemp < 9.0) { curMinTemp = 9.0 }
			if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
		} else {
			if(curMinTemp < 50) { curMinTemp = 50 }
			if(curMaxTemp > 90) { curMaxTemp = 90 }
		}
		sendEvent(name:'coolingSetpointMin', value: curMinTemp, unit: state?.tempUnit, descriptionText: "Cool SetpointMin is ${curMinTemp}${tUnitStr()}", state: "cool")
		sendEvent(name:'coolingSetpointMax', value: curMaxTemp, unit: state?.tempUnit, descriptionText: "Cool SetpointMax is ${curMaxTemp}${tUnitStr()}", state: "cool")
	}
}

def hasLeafEvent(Boolean hasLeaf) {
	def leaf = device.currentState("hasLeaf")?.value
	def lf = hasLeaf ? "On" : "Off"
	state?.hasLeaf = hasLeaf
	if(isStateChange(device, "hasLeaf", lf.toString())) {
		Logger("Leaf is set to (${lf}) | Previous State: (${leaf})")
		sendEvent(name:'hasLeaf', value: lf,  descriptionText: "Leaf: ${lf}", displayed: false, isStateChange: true, state: lf)
	}
}

def humidityEvent(humidity) {
	def hum = device.currentState("humidity")?.value
	if(isStateChange(device, "humidity", humidity.toString())) {
		Logger("Humidity is (${humidity}) | Previous State: (${hum})")
		sendEvent(name:'humidity', value: humidity, unit: "%", descriptionText: "Humidity is ${humidity}", displayed: false, isStateChange: true)
	}
}

def etaEvent(String eta) {
	def oeta = device.currentState("etaBegin")?.value
	if(isStateChange(device, "etaBegin", eta.toString())) {
		Logger("Eta Begin is (${eta}) | Previous State: (${oeta})")
		sendEvent(name:'etaBegin', value: eta, descriptionText: "Eta is ${eta}", displayed: true, isStateChange: true)
	}
}

def presenceEvent(String presence) {
	// log.trace "presenceEvent($presence)"
	def val = getPresence()
	def pres = (presence == "away" || presence == "auto-away") ? "not present" : "present"
	def nestPres = state?.nestPresence
	def newNestPres = (pres == "present") ? "home" : ((presence == "auto-away") ? "auto-away" : "away")
	def statePres = state?.isPresent
	state?.isPresent = (pres == "not present") ? false : true
	state?.nestPresence = newNestPres
	if(isStateChange(device, "presence", pres.toString()) || isStateChange(device, "nestPresence", newNestPres.toString()) || nestPres == null) {
		def chgType = ""
		chgType += isStateChange(device, "presence", pres.toString()) ? "HE" : ""
		chgType += isStateChange(device, "presence", pres.toString()) && isStateChange(device, "nestPresence", newNestPres.toString()) ? " | " : ""
		chgType += isStateChange(device, "nestPresence", newNestPres.toString()) ? "Nest" : ""
		Logger("${chgType} Presence: ${pres.toString().capitalize()} | Previous State: ${val.toString().capitalize()} | State Variable: ${statePres}")
		sendEvent(name: 'presence', value: pres, descriptionText: "Device is: ${pres}", displayed: false, isStateChange: true, state: pres )
		sendEvent(name: 'nestPresence', value: newNestPres, descriptionText: "Nest Presence is: ${newNestPres}", displayed: true, isStateChange: true )
	}
}

def hvacModeEvent(mode) {
	def hvacMode = !state?.hvac_mode ? device.currentState("thermostatMode")?.value?.toString() : state?.hvac_mode
	def newMode = (mode == "heat-cool") ? "auto" : mode
/*
	if(mode == "eco") {
		if(state?.can_cool && state?.can_heat) { newMode = "auto" }
		else if(state?.can_heat) { newMode = "heat" }
		else if(state?.can_cool) { newMode = "cool" }
	}
*/
	state?.hvac_mode = newMode
	if(!hvacMode.equals(newMode)) {
		Logger("Hvac Mode is (${newMode.toString().capitalize()}) | Previous State: (${hvacMode.toString().capitalize()})")
		sendEvent(name: "thermostatMode", value: newMode, descriptionText: "HVAC mode is ${newMode} mode", displayed: true, isStateChange: true)
	}

	def oldnestmode = state?.nestHvac_mode
	newMode = (mode == "heat-cool") ? "auto" : mode
	state?.nestHvac_mode = newMode
	if(!oldnestmode.equals(newMode)) {
		Logger("NEST Hvac Mode is (${newMode.toString().capitalize()}) | Previous State: (${oldnestmode.toString().capitalize()})")
		sendEvent(name: "nestThermostatMode", value: newMode, descriptionText: "Nest HVAC mode is ${newMode} mode", displayed: true, isStateChange: true)
	}
}

def hvacPreviousModeEvent(mode) {
	def hvacMode = !state?.previous_hvac_mode ? device.currentState("previousthermostatMode")?.value?.toString() : state?.previous_hvac_mode
	def newMode = (mode == "heat-cool") ? "auto" : mode
	state?.previous_hvac_mode = newMode
	if(mode != "" && isStateChange(device, "previousthermostatMode", newMode?.toString())) {
		Logger("Hvac Previous Mode is (${newMode.toString().capitalize()}) | Previous State: (${hvacMode.toString().capitalize()})")
		sendEvent(name: "previousthermostatMode", value: newMode?.toString(), descriptionText: "HVAC Previous mode is ${newMode} mode", displayed: true, isStateChange: true)
	}
}

def fanModeEvent(fanActive) {
	def val = state?.has_fan ? ((fanActive == "true") ? "on" : "auto") : "disabled"
	def fanMode = device.currentState("thermostatFanMode")?.value
	if(isStateChange(device, "thermostatFanMode", val.toString())) {
		Logger("Fan Mode: (${val.toString().capitalize()}) | Previous State: (${fanMode.toString().capitalize()})")
		sendEvent(name: "thermostatFanMode", value: val, descriptionText: "Fan Mode is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

def nestoperatingStateEvent(opState=null) {
	def nesthvacState = device.currentState("nestThermostatOperatingState")?.value?.toString()
	def operState = opState == null ? nesthvacState : opState
	if(operState == null) { return }
	if(isStateChange(device, "nestThermostatOperatingState", operState.toString())) {
		Logger("nestOperatingState is (${operState.toString().capitalize()}) | Previous State: (${nesthvacState.toString().capitalize()})")
		sendEvent(name: 'nestThermostatOperatingState', value: operState, descriptionText: "Device is ${operState}")
	}
}

def operatingStateEvent(opState=null) {
	def operState = opState == null ? device.currentState("nestThermostatOperatingState")?.value?.toString() : opState
	if(operState == null) { return }

	operState = (operState == "off") ? "idle" : operState

	def newoperState = operState
	def fanOn = device.currentState("thermostatFanMode")?.value?.toString() == "on" ? true : false
	if (fanOn && operState == "idle") { newoperState = "fan only" }

	def hvacState = device.currentState("thermostatOperatingState")?.value?.toString()
	if(isStateChange(device, "thermostatOperatingState", newoperState.toString())) {
		Logger("OperatingState is (${newoperState.toString().capitalize()}) | Previous State: (${hvacState.toString().capitalize()})")
		sendEvent(name: 'thermostatOperatingState', value: newoperState, descriptionText: "Device is ${newoperState}", displayed: true, isStateChange: true)
	}
}

def tempLockOnEvent(isLocked) {
	def curState = device.currentState("tempLockOn")?.value?.toString()
	def newState = isLocked?.toString()
	state?.tempLockOn = newState
	if(isStateChange(device, "tempLockOn", newState.toString())) {
		Logger("Temperature Lock is set to (${newState}) | Previous State: (${curState})")
		sendEvent(name:'tempLockOn', value: newState,  descriptionText: "Temperature Lock: ${newState}", displayed: false, isStateChange: true, state: newState)
	}
}

def lockedTempEvent(Double minTemp, Double maxTemp) {
	def curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
	def curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	//def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(curMinTemp != minTemp || curMaxTemp != maxTemp) {
		Logger("Temperature Lock Minimum is (${minTemp}) | Previous Temp: (${curMinTemp})")
		Logger("Temperature Lock Maximum is (${maxTemp}) | Previous Temp: (${curMaxTemp})")
		sendEvent(name:'lockedTempMin', value: minTemp, unit: state?.tempUnit, descriptionText: "Temperature Lock Minimum is ${minTemp}${state?.tempUnit}", displayed: true, isStateChange: true)
		sendEvent(name:'lockedTempMax', value: maxTemp, unit: state?.tempUnit, descriptionText: "Temperature Lock Maximum is ${maxTemp}${state?.tempUnit}", displayed: true, isStateChange: true)
	}
}

def safetyTempsEvent(safetyTemps) {
	def curMinTemp = device.currentState("safetyTempMin")?.value?.toDouble()
	def curMaxTemp = device.currentState("safetyTempMax")?.value?.toDouble()
	def newMinTemp = safetyTemps && safetyTemps?.min ? safetyTemps?.min.toDouble() : 0
	def newMaxTemp = safetyTemps && safetyTemps?.max ? safetyTemps?.max.toDouble() : 0

	//def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(curMinTemp != newMinTemp || curMaxTemp != newMaxTemp) {
		Logger("Safety Temperature Minimum is (${newMinTemp}${state?.tempUnit}) | Previous Temp: (${curMinTemp}${state?.tempUnit})")
		Logger("Safety Temperature Maximum is (${newMaxTemp}${state?.tempUnit}) | Previous Temp: (${curMaxTemp}${state?.tempUnit})")
		sendEvent(name:'safetyTempMin', value: newMinTemp, unit: state?.tempUnit, descriptionText: "Safety Temperature Minimum is ${newMinTemp}${state?.tempUnit}", displayed: true, isStateChange: true)
		sendEvent(name:'safetyTempMax', value: newMaxTemp, unit: state?.tempUnit, descriptionText: "Safety Temperature Maximum is ${newMaxTemp}${state?.tempUnit}", displayed: true, isStateChange: true)
		// checkSafetyTemps()
	}
}

def checkSafetyTemps() {
	def curMinTemp = device.currentState("safetyTempMin")?.value
	def curMaxTemp = device.currentState("safetyTempMax")?.value
	def curTemp = device.currentState("temperature")?.value
	def curRangeStr = device.currentState("safetyTempExceeded")?.value
	def outOfRange = false
	if(curMinTemp && curTemp < curMinTemp ) { outOfRange = true }
	if(curMaxTemp && curTemp > curMaxTemp) { outOfRange = true }
	//log.debug "curMinTemp: $curMinTemp | curMaxTemp: $curMaxTemp | curTemp: $curTemp | outOfRange: $outOfRange | curRangeStr: $curRangeStr"
	// Logger("checkSafetyTemps: (curMinTemp: ${curMinTemp} | curMaxTemp: ${curMaxTemp} | curTemp: ${curTemp} | exceeded: ${outOfRange} | curRangeStr: ${curRangeStr})")
	if(isStateChange(device, "safetyTempExceeded", outOfRange.toString())) {
		sendEvent(name:'safetyTempExceeded', value: outOfRange.toString(), descriptionText: "Safety Temperature ${outOfRange ? "Exceeded" : "OK"} ${curTemp}${state?.tempUnit}", displayed: true, isStateChange: true)
		Logger("Safety Temperature Exceeded is (${outOfRange}) | Current Temp: (${curTemp}${state?.tempUnit}) | Min: ($curMinTemp${state?.tempUnit}) | Max: ($curMaxTemp${state?.tempUnit})")
	}
}

def apiStatusEvent(issueDesc) {
	def curStat = device.currentState("apiStatus")?.value
	def newStat = issueDesc
	if(isStateChange(device, "apiStatus", newStat.toString())) {
		Logger("API Status is: (${newStat.toString().capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
	}
}

def canHeatCool(canHeat, canCool) {
	def supportedThermostatModes = ["off", "eco"]
	state?.can_heat = !canHeat ? false : true
	if(state?.can_heat) { supportedThermostatModes << "heat" }
	state?.can_cool = !canCool ? false : true
	if(state?.can_cool) { supportedThermostatModes << "cool" }
	state?.has_auto = (canCool && canHeat) ? true : false
	if(state?.can_heat && state?.can_cool) { supportedThermostatModes << "auto" }
	if(isStateChange(device, "canHeat", state?.can_heat.toString())) {
		sendEvent(name: "canHeat", value: state?.can_heat.toString())
	}
	if(isStateChange(device, "canCool", state?.can_cool.toString())) {
		sendEvent(name: "canCool", value: state?.can_cool.toString())
	}
	if(isStateChange(device, "hasAuto", state?.has_auto.toString())) {
		sendEvent(name: "hasAuto", value: state?.has_auto.toString())
	}
	if(state?.supportedThermostatModes != supportedThermostatModes) {
		sendEvent(name: "supportedThermostatModes", value: supportedThermostatModes)
		state?.supportedThermostatModes = supportedThermostatModes.collect()
	}

	def nestSupportedThermostatModes = supportedThermostatModes.collect()
	//nestSupportedThermostatModes << "eco"
	if(state?.supportedNestThermostatModes != nestSupportedThermostatModes) {
		sendEvent(name: "supportedNestThermostatModes", value: nestSupportedThermostatModes)
		state?.supportedNestThermostatModes = nestSupportedThermostatModes.collect()
	}
}

def hasFan(hasFan) {
	def supportedFanModes = []
	state?.has_fan = (hasFan == "true") ? true : false
	if(isStateChange(device, "hasFan", hasFan.toString())) {
		sendEvent(name: "hasFan", value: hasFan.toString())
	}
	if(state?.has_fan) {
		supportedFanModes = ["auto","on"]
	}
	if(state?.supportedThermostatFanModes != supportedFanModes) {
		sendEvent(name: "supportedThermostatFanModes", value: supportedFanModes)
		state?.supportedThermostatFanModes = supportedFanModes.collect()
	}
}

def isEmergencyHeat(val) {
	state?.is_using_emergency_heat = !val ? false : true
}

def clearHeatingSetpoint() {
	sendEvent(name:'heatingSetpoint', value: "",  descriptionText: "Clear Heating Setpoint", displayed: true )
	sendEvent(name:'heatingSetpointMin', value: "",  descriptionText: "Clear Heating SetpointMin", displayed: false )
	sendEvent(name:'heatingSetpointMax', value: "",  descriptionText: "Clear Heating SetpointMax", displayed: false )
//	state?.allowHeat = false
}

def clearCoolingSetpoint() {
	sendEvent(name:'coolingSetpoint', value: "",  descriptionText: "Clear Cooling Setpoint", displayed: true)
	sendEvent(name:'coolingSetpointMin', value: "",  descriptionText: "Clear Cooling SetpointMin", displayed: false)
	sendEvent(name:'coolingSetpointMax', value: "",  descriptionText: "Clear Cooling SetpointMax", displayed: false)
//	state?.allowCool = false
}

def getCoolTemp() {
	def t0 = device.currentState("coolingSetpoint")?.value
	return !t0 ? 0 : t0
}

def getHeatTemp() {
	def t0 = device.currentState("heatingSetpoint")?.value
	return !t0 ? 0 : t0
}

def getFanMode() {
	def t0 = device.currentState("thermostatFanMode")?.value
	return !t0 ? "unknown" : t0.toString()
}

def getHvacMode() {
	return !state?.nestHvac_mode ? device.currentState("nestThermostatMode")?.value?.toString() : state?.nestHvac_mode
}

def getHvacState() {
	def t0 = device.currentState("thermostatOperatingState")?.value
	return !t0 ? "unknown" : t0.toString()
}

def getNestPresence() {
	return !state?.nestPresence ? device.currentState("nestPresence")?.value?.toString() : state?.nestPresence
}

def getPresence() {
	def t0 = device.currentState("presence")?.value
	return !t0 ? "present" : t0.toString()
}

def getTargetTemp() {
	def t0 = device.currentState("targetTemperature")?.value
	return !t0 ? 0 : t0
}

def getThermostatSetpoint() {
	def t0 = device.currentState("thermostatSetpoint")?.value
	return !t0 ? 0 : t0
}

def getTemp() {
	def t0 = device.currentState("temperature")?.value
	return !t0 ? 0 : t0
}

def getHumidity() {
	def t0 = device.currentState("humidity")?.value
	return !t0 ? 0 : t0
}

def getTempWaitVal() {
	return state?.childWaitVal ? state?.childWaitVal.toInteger() : 3
}

def wantMetric() { return (state?.tempUnit == "C") }

def getDevTypeId() { return device?.getDevTypeId() }

/************************************************************************************************
 |							Temperature Setpoint Functions for Buttons							|
 *************************************************************************************************/
void heatingSetpointUp() {
	//Logger("heatingSetpointUp()...", "trace")
	def operMode = getHvacMode()
	if( operMode in ["heat", "eco", "auto"] ) {
		levelUpDown(1,"heat")
	}
}

void heatingSetpointDown() {
	//Logger("heatingSetpointDown()...", "trace")
	def operMode = getHvacMode()
	if( operMode in ["heat","eco", "auto"] ) {
		levelUpDown(-1, "heat")
	}
}

void coolingSetpointUp() {
	//Logger("coolingSetpointUp()...", "trace")
	def operMode = getHvacMode()
	if( operMode in ["cool","eco", "auto"] ) {
		levelUpDown(1, "cool")
	}
}

void coolingSetpointDown() {
	//Logger("coolingSetpointDown()...", "trace")
	def operMode = getHvacMode()
	if( operMode in ["cool", "eco", "auto"] ) {
		levelUpDown(-1, "cool")
	}
}

void levelUp() {
	levelUpDown(1)
}

void levelDown() {
	levelUpDown(-1)
}

void levelUpDown(tempVal, chgType = null) {
	//Logger("levelUpDown()...($tempVal | $chgType)", "trace")
	def hvacMode = getHvacMode()

	if(canChangeTemp()) {
		// From RBOY https://community.smartthings.com/t/multiattributetile-value-control/41651/23
		def upLevel

		if(!state?.lastLevelUpDown) { state?.lastLevelUpDown = 0 } // If it isn't defined lets baseline it

		if((state?.lastLevelUpDown == 1) && (tempVal == 1)) { upLevel = true } //Last time it was 1 and again it's 1 its increase

		else if((state?.lastLevelUpDown == 0) && (tempVal == 0)) { upLevel = false } //Last time it was 0 and again it's 0 then it's decrease

		else if((state?.lastLevelUpDown == -1) && (tempVal == -1)) { upLevel = false } //Last time it was -1 and again it's -1 then it's decrease

		else if((tempVal - state?.lastLevelUpDown) > 0) { upLevel = true } //If it's increasing then it's up

		else if((tempVal - state?.lastLevelUpDown) < 0) { upLevel = false } //If it's decreasing then it's down

		else { log.error "UNDEFINED STATE, CONTACT DEVELOPER. Last level ${state?.lastLevelUpDown}, Current level, $value" }

		state?.lastLevelUpDown = tempVal // Save it

		def targetVal = 0.0
		def curHeatpoint = device.currentState("heatingSetpoint")?.value
		def curCoolpoint = device.currentState("coolingSetpoint")?.value
		def curThermSetpoint = device.currentState("thermostatSetpoint")?.value
		targetVal = curThermSetpoint ?: 0.0
		if(hvacMode == "auto") {
			if(chgType == "cool") {
				targetVal = curCoolpoint
				curThermSetpoint = targetVal
			}
			if(chgType == "heat") {
				targetVal = curHeatpoint
				curThermSetpoint = targetVal
			}
		}
		def locked = state?.tempLockOn.toBoolean()
		def curMinTemp
		def curMaxTemp = 100.0

		if(locked) {
			curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
			curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
		}
		if(wantMetric()) {
			if(curMinTemp < 9.0) { curMinTemp = 9.0 }
			if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
		} else {
			if(curMinTemp < 50) { curMinTemp = 50 }
			if(curMaxTemp > 90) { curMaxTemp = 90 }
		}
		if(upLevel) {
			//Logger("Increasing by 1 increment")
			if(wantMetric()) {
				targetVal = targetVal.toDouble() + 0.5
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			} else {
				targetVal = targetVal.toDouble() + 1.0
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			}
		} else {
			//Logger("Reducing by 1 increment")
			if(wantMetric()) {
				targetVal = targetVal.toDouble() - 0.5
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			} else {
				targetVal = targetVal.toDouble() - 1.0
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			}
		}

		if(targetVal != curThermSetpoint ) {
			pauseEvent("true")
			switch (hvacMode) {
				case "heat":
					if(state?.oldHeat == null) { state?.oldHeat = curHeatpoint}
					thermostatSetpointEvent(targetVal)
					heatingSetpointEvent(targetVal)
					if(!chgType) { chgType = "" }
					scheduleChangeSetpoint()
					Logger("Sending changeSetpoint(Temp: ${targetVal})")
					break
				case "cool":
					if(state?.oldCool == null) { state?.oldCool = curCoolpoint}
					thermostatSetpointEvent(targetVal)
					coolingSetpointEvent(targetVal)
					if(!chgType) { chgType = "" }
					scheduleChangeSetpoint()
					Logger("Sending changeSetpoint(Temp: ${targetVal})")
					break
				case "auto":
					if(chgType) {
						switch (chgType) {
							case "cool":
								if(state?.oldCool == null) { state?.oldCool = curCoolpoint}
								coolingSetpointEvent(targetVal)
								scheduleChangeSetpoint()
								Logger("Sending changeSetpoint(Temp: ${targetVal})")
								break
							case "heat":
								if(state?.oldHeat == null) { state?.oldHeat = curHeatpoint}
								heatingSetpointEvent(targetVal)
								scheduleChangeSetpoint()
								Logger("Sending changeSetpoint(Temp: ${targetVal})")
								break
							default:
								Logger("Unable to Change Temp while in Current Mode: ($chgType}!!!", "warn")
								break
						}
					} else { Logger("Temp Change without a chgType is not supported!!!", "warn") }
					break
				default:
					pauseEvent("false")
					Logger("Unsupported Mode Received: ($hvacMode}!!!", "warn")
					break
			}
		}
	} else { Logger("levelUpDown: Cannot adjust temperature due to hvacMode ${hvacMode}") }
}

def scheduleChangeSetpoint() {
	if(getLastChangeSetpointSec() > 7) {
		state?.lastChangeSetpointDt = getDtNow()
		runIn( 6, "changeSetpoint", [overwrite: true] )
	}
}

def getLastChangeSetpointSec() { return !state?.lastChangeSetpointDt ? 100000 : GetTimeDiffSeconds(state?.lastChangeSetpointDt).toInteger() }

def getSettingVal(var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}

def formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

//Returns time differences is seconds
def GetTimeDiffSeconds(lastDate) {
	if(lastDate?.contains("dtNow")) { return 10000 }
	def now = new Date()
	def lastDt = Date.parse("E MMM dd HH:mm:ss z yyyy", lastDate)
	def start = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(lastDt)).getTime()
	def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(now)).getTime()
	def diff = (int) (long) (stop - start) / 1000 //
	return diff
}

def getTimeDiffSeconds(strtDate, stpDate=null, methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if(strtDate) {
		//if(strtDate?.contains("dtNow")) { return 10000 }
		def now = new Date()
		def stopVal = stpDate ? stpDate.toString() : formatDt(now)
		def startDt = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate)
		def stopDt = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal)
		def start = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(startDt)).getTime()
		def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		def diff = (int) (long) (stop - start) / 1000 //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}

// Nest does not allow temp changes in off, eco modes
def canChangeTemp() {
	//Logger("canChangeTemp()...", "trace")
	if(state?.nestHvac_mode != "eco") {
		def hvacMode = getHvacMode()
		switch (hvacMode) {
			case "heat":
				return true
				break
			case "cool":
				return true
				break
			case "auto":
				return true
				break
			default:
				return false
				break
		}
	} else { return false }
}

void changeSetpoint() {
	//Logger("changeSetpoint()... ($val)", "trace")
	def hvacMode = getHvacMode()
	if(canChangeTemp()) {
		def md
		def curHeatpoint = getHeatTemp()
		def curCoolpoint = getCoolTemp()
		// Logger("changeSetpoint()... hvacMode: ${hvacMode} curHeatpoint: ${curHeatpoint}  curCoolpoint: ${curCoolpoint} oldCool: ${state?.oldCool} oldHeat: ${state?.oldHeat}", "trace")
		switch (hvacMode) {
			case "heat":
				state?.oldHeat = null
				setHeatingSetpoint(curHeatpoint)
				break
			case "cool":
				state?.oldCool = null
				setCoolingSetpoint(curCoolpoint)
				break
			case "auto":
				if( (state?.oldCool != null) && (state?.oldHeat == null) ) { md = "cool"}
				if( (state?.oldCool == null) && (state?.oldHeat != null) ) { md = "heat"}
				if( (state?.oldCool != null) && (state?.oldHeat != null) ) { md = "both"}

				def heatFirst
				if(md) {
					if(curHeatpoint >= curCoolpoint) {
						Logger("changeSetpoint: Received an Invalid Temp while in AUTO mode... | Heat: (${curHeatpoint})/Cool: (${curCoolpoint})", "warn")
					} else {
						if("${md}" == "heat") { state?.oldHeat = null; setHeatingSetpoint(curHeatpoint) }
						else if("${md}" == "cool") { state?.oldCool = null; setCoolingSetpoint(curCoolpoint) }
						else if("${md}" == "both") {
							if(curHeatpoint <= state?.oldHeat) { heatfirst = true }
							else if(curCoolpoint >= state?.oldCool) { heatFirst = false }
							else if(curHeatpoint > state?.oldHeat) { heatFirst = false }
							else { heatFirst = true }
							if(heatFirst) {
								state?.oldHeat = null
								setHeatingSetpoint(curHeatpoint)
								state?.oldCool = null
								setCoolingSetpoint(curCoolpoint)
							} else {
								state?.oldCool = null
								setCoolingSetpoint(curCoolpoint)
								state?.oldHeat = null
								setHeatingSetpoint(curHeatpoint)
							}
						}
					}
				} else {
					Logger("changeSetpoint: Received Invalid Temp Type... ${md}", "warn")
					state?.oldCool = null
					state?.oldHeat = null
				}
				break
			default:
				if(curHeatpoint > curCoolpoint) {
					Logger("changeSetpoint: Received an Invalid Temp while in AUTO mode... ${curHeatpoint} ${curCoolpoint} ${val}", "warn")
				}
				//thermostatSetpointEvent(temp)
				break
		}
	} else { Logger("changeSetpoint: Cannot adjust Temp Due to hvacMode: ${hvacMode}") }
	pauseEvent("false")
}

// Nest Only allows F temperatures as #.0  and C temperatures as either #.0 or #.5
void setHeatingSetpoint(temp) {
	setHeatingSetpoint(temp.toDouble())
}

void setHeatingSetpoint(Double reqtemp) {
	// Logger("setHeatingSetpoint()... ($reqtemp)", "trace")
	def hvacMode = getHvacMode()
	def tempUnit = state?.tempUnit
	def temp = 0.0
	def canHeat = state?.can_heat.toBoolean()
	def result = false
	def locked = state?.tempLockOn.toBoolean()
	def curMinTemp
	def curMaxTemp = 100.0

	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	// Logger("Heat Temp Received: ${reqtemp} (${tempUnit}) | Temp Locked: ${locked}")
	if(canHeat && state?.nestHvac_mode != "eco") {
		switch (tempUnit) {
			case "C":
				temp = Math.round(reqtemp.round(1) * 2) / 2.0f //
				if(curMinTemp < 9.0) { curMinTemp = 9.0 }
				if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Heat Temp: ($temp${tUnitStr()})")
					if(hvacMode == 'auto') {
						parent.setTargetTempLow(this, tempUnit, temp, virtType())
						heatingSetpointEvent(temp)
					}
					if(hvacMode == 'heat') {
						parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp)
						heatingSetpointEvent(temp)
					}
				}
				result = true
				break
			case "F":
				temp = reqtemp.round(0).toInteger()
				if(curMinTemp < 50) { curMinTemp = 50 }
				if(curMaxTemp > 90) { curMaxTemp = 90 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Heat Temp: ($temp${tUnitStr()})")
					if(hvacMode == 'auto') {
						parent.setTargetTempLow(this, tempUnit, temp, virtType())
						heatingSetpointEvent(temp)
					}
					if(hvacMode == 'heat') {
						parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp)
						heatingSetpointEvent(temp)
					}
				}
				result = true
				break
			default:
				Logger("No Temperature Unit Found: ($tempUnit)", "warn")
				break
		}
	} else {
		Logger("Skipping Heat Change | canHeat: ${canHeat} | hvacMode: ${hvacMode}")
		result = false
	}
}

void setCoolingSetpoint(temp) {
	setCoolingSetpoint( temp.toDouble())
}

void setCoolingSetpoint(Double reqtemp) {
	// Logger("setCoolingSetpoint()... ($reqtemp)", "trace")
	def hvacMode = getHvacMode()
	def temp = 0.0
	def tempUnit = state?.tempUnit
	def canCool = state?.can_cool.toBoolean()
	def result = false
	def locked = state?.tempLockOn.toBoolean()
	def curMinTemp
	def curMaxTemp = 100.0

	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	// Logger("Cool Temp Received: (${reqtemp}${tempUnit}) | Temp Locked: ${locked}")
	if(canCool && state?.nestHvac_mode != "eco") {
		switch (tempUnit) {
			case "C":
				temp = Math.round(reqtemp.round(1) * 2) / 2.0f //
				if(curMinTemp < 9.0) { curMinTemp = 9.0 }
				if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Cool Temp: ($temp${tUnitStr()})")
					if(hvacMode == 'auto') {
						parent.setTargetTempHigh(this, tempUnit, temp, virtType())
						coolingSetpointEvent(temp)
					}
					if(hvacMode == 'cool') {
						parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp)
						coolingSetpointEvent(temp)
					}
				}
				result = true
				break

			case "F":
				temp = reqtemp.round(0).toInteger()
				if(curMinTemp < 50) { curMinTemp = 50 }
				if(curMaxTemp > 90) { curMaxTemp = 90 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Cool Temp: ($temp${tUnitStr()})")
					if(hvacMode == 'auto') {
						parent.setTargetTempHigh(this, tempUnit, temp, virtType())
						coolingSetpointEvent(temp)
					}
					if(hvacMode == 'cool') {
						parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp)
						coolingSetpointEvent(temp)
					}
				}
				result = true
				break
			default:
				Logger("No Temperature Unit Found: ($tempUnit)", "warn")
				break
		}
	} else {
		Logger("Skipping Cool Change | canCool: ${canCool} | hvacMode: ${hvacMode}")
		result = false
	}
}

/************************************************************************************************
 |									NEST PRESENCE FUNCTIONS										|
 *************************************************************************************************/
void setPresence() {
	Logger("setPresence()...", "trace")
	def pres = getNestPresence()
	Logger("Current Nest Presence: ${pres}", "trace")
	if(pres == "auto-away" || pres == "away") {
		if(parent.setStructureAway(this, "false", virtType())) { presenceEvent("home") }
	}
	else if(pres == "home") {
		if(parent.setStructureAway(this, "true", virtType())) { presenceEvent("away") }
	}
}

// backward compatibility for previous nest thermostat (and rule machine)
void away() {
	Logger("away()...", "trace")
	setAway()
}

// backward compatibility for previous nest thermostat (and rule machine)
void present() {
	Logger("present()...", "trace")
	setHome()
}

def setAway() {
	Logger("setAway()...", "trace")
	if(parent.setStructureAway(this, "true", virtType())) { presenceEvent("away") }
}

def setHome() {
	Logger("setHome()...", "trace")
	if(parent.setStructureAway(this, "false", virtType()) ) { presenceEvent("home") }
}

/*
def setNestEta(tripId, begin, end){
	Logger("setNestEta()...", "trace")
	parent?.setEtaState(this, ["trip_id": "${tripId}", "estimated_arrival_window_begin": "${begin}", "estimated_arrival_window_end": "${end}" ], virtType() )
}

def cancelNestEta(tripId){
	Logger("cancelNestEta()...", "trace")
	parent?.cancelEtaState(this, "${tripId}", virtType() )
}
*/

/************************************************************************************************
 |										HVAC MODE FUNCTIONS										|
 ************************************************************************************************/

def getHvacModes() {
	//Logger("Building Modes list")
	def modesList = ['off']
	if( state?.can_heat == true ) { modesList.push('heat') }
	if( state?.can_cool == true ) { modesList.push('cool') }
	if( state?.can_heat == true && state?.can_cool == true ) { modesList.push('auto') }
	modesList.push('eco')
	Logger("Modes = ${modesList}")
	return modesList
}

/*
def changeMode() {
	//Logger("changeMode..")
	def currentMode = getHvacMode()
	def lastTriedMode = currentMode ?: "off"
	def modeOrder = getHvacModes()
	def next = { modeOrder[modeOrder.indexOf(it) + 1] ?: modeOrder[0] }
	def nextMode = next(lastTriedMode)
	Logger("changeMode() | currentMode: ${currentMode} | lastModeTried: ${lastTriedMode} | nextMode: ${nextMode}", "trace")
	setHvacMode(nextMode)
}
*/

def setHvacMode(nextMode) {
	Logger("setHvacMode(${nextMode})")
	if(nextMode in getHvacModes()) {
		state?.lastTriedMode = nextMode
		"$nextMode"()
	} else {
		Logger("Invalid Mode '$nextMode'")
	}
}

void off() {
	Logger("off()...", "trace")
	pauseEvent("true")
	hvacModeEvent("off")
	if(parent.setHvacMode(this, "off", virtType())) {
		pauseEvent("false")
	}
}

void heat() {
	Logger("heat()...", "trace")
	pauseEvent("true")
	hvacModeEvent("heat")
	if(parent.setHvacMode(this, "heat", virtType())) {
		pauseEvent("false")
	}
}

void emergencyHeat() {
	Logger("emergencyHeat()...", "trace")
	Logger("Emergency Heat setting not allowed", "warn")
}

void cool() {
	Logger("cool()...", "trace")
	pauseEvent("true")
	hvacModeEvent("cool")
	if(parent.setHvacMode(this, "cool", virtType())) {
		pauseEvent("false")
	}
}

void auto() {
	Logger("auto()...", "trace")
	pauseEvent("true")
	hvacModeEvent("auto")
	if(parent.setHvacMode(this, "heat-cool", virtType())) {
		pauseEvent("false")
	}
}

void eco() {
	Logger("eco()...", "trace")
	pauseEvent("true")
	hvacModeEvent("eco")
	if(parent.setHvacMode(this, "eco", virtType())) {
		pauseEvent("false")
	}
}

void setThermostatMode(modeStr) {
	Logger("setThermostatMode()...", "trace")
	switch(modeStr) {
		case "auto":
			auto()
			break
		case "heat":
			heat()
			break
		case "cool":
			cool()
			break
		case "eco":
			eco()
			break
		case "off":
			off()
			break
		case "emergency heat":
			emergencyHeat()
			break
		default:
			Logger("setThermostatMode Received an Invalid Request: ${modeStr}", "warn")
			break
	}
}


/************************************************************************************************
 |										FAN MODE FUNCTIONS										|
 *************************************************************************************************/
/*
def changeFanMode() {
	def cur = device.currentState("thermostatFanMode")?.value
	if(cur == "on" || !cur) {
		setThermostatFanMode("auto")
	} else {
		setThermostatFanMode("on")
	}
}
*/

void fanOn() {
	try {
		Logger("fanOn()...", "trace")
		if(state?.has_fan.toBoolean()) {
			if(parent.setFanMode(this, true, virtType()) ) { fanModeEvent("true") }
		} else { Logger("Error setting fanOn", "error") }
	}
	catch (ex) {
		log.error "fanOn Exception: ${ex?.message}"
	}
}

/*
// non standard by Hubitat Capabilities Thermostat Fan Mode
void fanOff() {
	Logger("fanOff()...", "trace")
	fanAuto()
}
*/

void fanCirculate() {
	Logger("fanCirculate()...", "trace")
	fanOn()
}

void fanAuto() {
	try {
		Logger("fanAuto()...", "trace")
		if(state?.has_fan.toBoolean()) {
			if(parent.setFanMode(this,false, virtType()) ) { fanModeEvent("false") }
		} else { Logger("Error setting fanAuto", "error") }
	}
	catch (ex) {
		log.error "fanAuto Exception: ${ex?.message}"
	}
}

void setThermostatFanMode(fanModeStr) {
	Logger("setThermostatFanMode($fanModeStr)...", "trace")
	switch(fanModeStr) {
		case "auto":
			fanAuto()
			break
		case "on":
			fanOn()
			break
		case "circulate":
			fanCirculate()
			break
/*		case "off":   // non standard by Hubitat Capabilities Thermostat Fan Mode
			fanOff()
			break
*/
		default:
			Logger("setThermostatFanMode Received an Invalid Request: ${fanModeStr}", "warn")
			break
	}
}

void setSchedule(obj) {
	Logger("setSchedule...", "trace")
}

/**************************************************************************
 |						LOGGING FUNCTIONS								  |
 **************************************************************************/

def lastN(String input, n) {
	return n > input?.size() ? input : input[-n..-1]
}

void Logger(msg, logType = "debug") {
	if(!logEnable || !msg) { return }
	def smsg = "${device.displayName} (v${devVer()}) | ${msg}"
	if(state?.enRemDiagLogging == null) {
		state?.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if(state?.enRemDiagLogging == null) {
			state?.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state?.enRemDiagLogging}"
	}
        if(state?.enRemDiagLogging) {
		def theId = lastN(device.getId().toString(),5)
                parent.saveLogtoRemDiagStore(smsg, logType, "Thermostat-${theId}")
        } else {
		switch (logType) {
			case "trace":
				log.trace "${msg}"
				break
			case "debug":
				log.debug "${msg}"
				break
			case "info":
				log.info "${msg}"
				break
			case "warn":
				log.warn "${msg}"
				break
			case "error":
				log.error "${msg}"
				break
			default:
				log.debug "${msg}"
				break
		}
	}
}
