/**
 *  Nest Thermostat
 *	Copyright (C) 2018, 2019 Anthony Santilli.
 *	Author: Anthony Santilli (@tonesto7), Eric Schott (@imnotbob)
 *  Modified: 12/10/2022
 */
//file:noinspection GroovyUnusedAssignment

import java.text.SimpleDateFormat
//import groovy.time.*
import groovy.transform.Field
import groovy.json.*
import groovy.transform.CompileStatic

static String devVer() { return "2.0.8" }
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
		attribute "usingEmergencyHeat", "string"

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

@Field static final String sECO='eco'
@Field static final String sAUTO='auto'
@Field static final String sHEAT='heat'
@Field static final String sCOOL='cool'
@Field static final String sOFF='off'
@Field static final String sHEATCOOL='heat-cool'
@Field static final String sCOOLINGSP='coolingSetpoint'
@Field static final String sHEATINGSP='heatingSetpoint'
@Field static final String sTEMP='temperature'
@Field static final String sNSTOPERSTATE='nestThermostatOperatingState'
@Field static final String sOPERSTATE='thermostatOperatingState'
@Field static final String sTRUE='true'
@Field static final String sFALSE='false'
@Field static final String sTRACE='trace'
@Field static final String sWARN='warn'
@Field static final String sBLK=''
@Field static final String sNULL=(String)null

void logsOff(){
	log.warn "${device?.displayName} debug logging disabled..."
	device.updateSetting("logEnable",[value:sFALSE,type:"bool"])
}

private Boolean virtType()	{ return bsIs('virtual') == true }

//static Boolean compileForC() { return false }
//private static Integer lowRange() { return compileForC() ? 9 : 50 }
//private static Integer highRange() { return compileForC() ? 32 : 90 }
//private static String getRange() { return "${lowRange()}..${highRange()}".toString() }

void initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	verifyDataAttr()
	if(!(Long)state.updatedLastRanAt || wnow() >= (Long)state.updatedLastRanAt + 2000) {
		state.updatedLastRanAt = wnow()
		checkVirtualStatus()
		state.isInstalled = true
		log.warn "debug logging is: ${logEnable} | description logging is: ${txtEnable}"
		if ((Boolean)logEnable) runIn(1800,logsOff)
	} else {
		log.trace "initialize(): Ran within last 2 seconds - SKIPPING"
	}
	state.remove("enRemDiagLogging")
	state.remove("supportedNestThermostatModes")
	state.remove("supportedThermostatModes")
	state.remove("supportedThermostatFanModes")
}

void verifyDataAttr() {
	if(!device?.getDataValue("manufacturer")) {
		updateDataValue("manufacturer", "Nest")
	}
	if(!device?.getDataValue("model")) {
		updateDataValue("model", (String)device?.name)
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
	log.warn "debug logging is: ${(Boolean)logEnable} | description logging is: ${txtEnable}"
	runIn( 5, "initialize", [overwrite: true] )
}

void checkVirtualStatus() {
	if(getDataValue("isVirtual") == sNULL && state.virtual != null) {
		Boolean res = (state.virtual instanceof Boolean) ? state.virtual : false
		Logger("Updating the device's 'isVirtual' data value to (${res})")
		updateDataValue("isVirtual", "${res}".toString())
	} else {
		Boolean dVal = getDataValue("isVirtual") == sTRUE
		if(dVal != state.virtual || state.virtual == null) {
			state.virtual = dVal
			Logger("Setting virtual to ${dVal?.toString()?.toUpperCase()}")
		}
	}
}

void stateRemove(key) {
	state.remove(key?.toString())
}

void parse(String description) {
	if(txtEnable) { Logger("Parsing '${description}'") }
}

void poll() {
	//Logger("Polling parent...")
	refresh()
}

void refresh() {
	pauseEvent(sFALSE)
	parent?.refresh(this)
}

void generateEvent(eventData) {
	String curData = device.currentState("pauseUpdates")?.value
	Boolean pauseUpd = !curData ? false : curData.toBoolean()
	if(pauseUpd) { Logger("Changes Paused! Device Command in Progress", sWARN); return }

	//Logger("processEvent Parsing data ${eventData}", sTRACE)
	try {
		if(eventData && eventData.data) {
			Map edd=(Map)eventData.data
			if(eventData.virt) { state.virtual = eventData.virt }
			if(virtType()) { nestTypeEvent("virtual") } else { nestTypeEvent("physical") }
			state.childWaitVal = (Integer)eventData.childWaitVal
			state.nestTimeZone = eventData.tz ?: null
			tempUnitEvent((String)getTemperatureScale())
			tempLockOnEvent(bIs(edd,'is_locked') == true)
			canHeatCool(bIs(edd,'can_heat'), bIs(edd,'can_cool'))
			hasFan(bIs(edd,'has_fan'))
			presenceEvent(sMs((Map)eventData,'pres'))
			etaEvent(sMs((Map)eventData,'etaBegin'))

//			String curMode = device.currentState("nestThermostatMode")?.value?.toString()
			hvacModeEvent(sMs(edd,'hvac_mode'))
//			String newMode = device?.currentState("nestThermostatMode")?.value?.toString()

			hvacPreviousModeEvent(sMs(edd,'previous_hvac_mode'))
			hasLeafEvent(bIs(edd,'has_leaf'))
			humidityEvent((Integer)edd.humidity)

			nestoperatingStateEvent(sMs(edd,'hvac_state'))
			fanModeEvent(bIs(edd,'fan_timer_active'))
			operatingStateEvent(sMs(edd,'hvac_state'))

			if(!sMs(edd,'last_connection')) { lastCheckinEvent(sNULL,null) }
			else { lastCheckinEvent(sMs(edd,'last_connection'), bIs(edd,'is_online')) }
			sunlightCorrectionEnabledEvent(bIs(edd,'sunlight_correction_enabled'))
			sunlightCorrectionActiveEvent(bIs(edd,'sunlight_correction_active'))
			timeToTargetEvent(sMs(edd,'time_to_target'), sMs(edd,'time_to_target_training'))
			softwareVerEvent(sMs(edd,'software_version'))
			//onlineStatusEvent(edd.is_online?.toString())
			apiStatusEvent((String)eventData.apiIssues)
			// safetyTempsEvent(eventData.safetyTemps)
			// comfortHumidityEvent(eventData.comfortHumidity)
			// comfortDewpointEvent(eventData.comfortDewpoint)
			emergencyHeatEvent(bIs(edd,'is_using_emergency_heat'))

/*
log.warn "edd.locked_temp_min_c "+getObjType(edd.locked_temp_min_c)
log.warn "edd.locked_temp_max_c "+getObjType(edd.locked_temp_max_c)
log.warn "edd.target_temperature_c "+getObjType(edd.target_temperature_c)
log.warn "edd.target_temperature_high_c "+getObjType(edd.target_temperature_high_c)
log.warn "edd.target_temperature_low_c "+getObjType(edd.target_temperature_low_c)
log.warn "edd.locked_temp_min_f "+getObjType(edd.locked_temp_min_f)
log.warn "edd.locked_temp_max_f "+getObjType(edd.locked_temp_max_f)
log.warn "edd.target_temperature_f "+getObjType(edd.target_temperature_f)
log.warn "edd.target_temperature_high_f "+getObjType(edd.target_temperature_high_f)
log.warn "edd.target_temperature_low_f "+getObjType(edd.target_temperature_low_f)
*/
			String hvacMode = sSs('nestHvac_mode')
			String tempUnit = sSs('tempUnit')
			Boolean has_auto=bsIs('has_auto')
			switch (tempUnit) {
				case "C":
					if((BigDecimal)edd.locked_temp_min_c && (BigDecimal)edd.locked_temp_max_c) { lockedTempEvent((Double)((BigDecimal)edd.locked_temp_min_c).toDouble(), (Double)((BigDecimal)edd.locked_temp_max_c).toDouble()) }
					Double temp = ((BigDecimal)edd.ambient_temperature_c)?.toDouble()
					temperatureEvent(temp)

					Double heatingSetpoint, coolingSetpoint, targetTemp
					heatingSetpoint = 0.0D
					coolingSetpoint = 0.0D
					targetTemp = ((BigDecimal)edd.target_temperature_c)?.toDouble()

					if(hvacMode == sCOOL) {
						coolingSetpoint = targetTemp
					}
					else if(hvacMode == sHEAT) {
						heatingSetpoint = targetTemp
					}
					else if(hvacMode == sAUTO) {
						coolingSetpoint = Math.round(((BigDecimal)edd.target_temperature_high_c)?.toDouble())
						heatingSetpoint = Math.round(((BigDecimal)edd.target_temperature_low_c)?.toDouble())
					}
					if(hvacMode == sECO) {
						if((BigDecimal)edd.eco_temperature_high_c) { coolingSetpoint = ((BigDecimal)edd.eco_temperature_high_c).toDouble() }
						else if((BigDecimal)edd.away_temperature_high_c) { coolingSetpoint = ((BigDecimal)edd.away_temperature_high_c).toDouble() }
						if((BigDecimal)edd.eco_temperature_low_c) { heatingSetpoint = ((BigDecimal)edd.eco_temperature_low_c).toDouble() }
						else if((BigDecimal)edd.away_temperature_low_c) { heatingSetpoint = ((BigDecimal)edd.away_temperature_low_c).toDouble() }
					}

					if(hvacMode in [sCOOL, sAUTO, sECO] && bsIs('can_cool')) {
						coolingSetpointEvent(coolingSetpoint)
						if(hvacMode == sECO && !has_auto) { targetTemp = coolingSetpoint }
					} else {
						clearCoolingSetpoint()
					}
					if(hvacMode in [sHEAT, sAUTO, sECO] && bsIs('can_heat')) {
						heatingSetpointEvent(heatingSetpoint)
						if(hvacMode == sECO && !has_auto) { targetTemp = heatingSetpoint }
					} else {
						clearHeatingSetpoint()
					}

					if(hvacMode in [sCOOL, sHEAT] || (hvacMode == sECO && !has_auto)) {
						thermostatSetpointEvent(targetTemp)
					} else {
						sendEvent(name:'thermostatSetpoint', value: sBLK, descriptionText: "Clear Thermostat Setpoint", displayed: true)
						sendEvent(name:'thermostatSetpointMin', value: sBLK, descriptionText: "Clear Thermostat SetpointMin", displayed: false)
						sendEvent(name:'thermostatSetpointMax', value: sBLK, descriptionText: "Clear Thermostat SetpointMax", displayed: false)
					}
					break

				case "F":
					if((Integer)edd.locked_temp_min_f && (Integer)edd.locked_temp_max_f) { lockedTempEvent((Double)((Integer)edd.locked_temp_min_f).toDouble(), (Double)((Integer)edd.locked_temp_max_f).toDouble()) }
					Integer temp = (Integer)edd.ambient_temperature_f
					temperatureEvent((Double)temp.toDouble())

					Integer heatingSetpoint, coolingSetpoint, targetTemp
					heatingSetpoint = 0
					coolingSetpoint = 0
					targetTemp = (Integer)edd.target_temperature_f

					if(hvacMode == sCOOL) {
						coolingSetpoint = targetTemp
					}
					else if(hvacMode == sHEAT) {
						heatingSetpoint = targetTemp
					}
					else if(hvacMode == sAUTO) {
						coolingSetpoint = (Integer)edd.target_temperature_high_f
						heatingSetpoint = (Integer)edd.target_temperature_low_f
					}
					else if(hvacMode == sECO) {
						if((Integer)edd.eco_temperature_high_f) { coolingSetpoint = (Integer)edd.eco_temperature_high_f }
						else if((Integer)edd.away_temperature_high_f) { coolingSetpoint = (Integer)edd.away_temperature_high_f }
						if((Integer)edd.eco_temperature_low_f) { heatingSetpoint = (Integer)edd.eco_temperature_low_f }
						else if((Integer)edd.away_temperature_low_f) { heatingSetpoint = (Integer)edd.away_temperature_low_f }
					}

					if(hvacMode in [sCOOL, sAUTO, sECO] && bsIs('can_cool')) {
						coolingSetpointEvent(coolingSetpoint as Double)
						if(hvacMode == sECO && !has_auto) { targetTemp = coolingSetpoint }
					} else {
						clearCoolingSetpoint()
					}
					if(hvacMode in [sHEAT, sAUTO, sECO] && bsIs('can_heat')) {
						heatingSetpointEvent(heatingSetpoint as Double)
						if(hvacMode == sECO && has_auto) { targetTemp = heatingSetpoint }
					} else {
						clearHeatingSetpoint()
					}
					if(hvacMode in [sCOOL, sHEAT] || (hvacMode == sECO && !has_auto)) {
						thermostatSetpointEvent(targetTemp as Double)
					} else {
						sendEvent(name:'thermostatSetpoint', value: sBLK, descriptionText: "Clear Thermostat Setpoint", displayed: true)
						sendEvent(name:'thermostatSetpointMin', value: sBLK, descriptionText: "Clear Thermostat SetpointMin", displayed: false)
						sendEvent(name:'thermostatSetpointMax', value: sBLK, descriptionText: "Clear Thermostat SetpointMax", displayed: false)
					}
					break

				default:
					Logger("no Temperature data $tempUnit")
					break
			}
			lastUpdatedEvent(false)
		}
	}
	catch (ex) {
		log.error "processEvent Exception: ${ex?.message}"
	}
}

Integer getStateSize()	{ return state.toString().length() }
Integer getStateSizePerc() { return (Integer) ((stateSize/100000)*100).toDouble().round(0) }

@SuppressWarnings('unused')
def getDataByName(String name) { state[name] ?: device.getDataValue(name) }

@SuppressWarnings('unused')
def getDeviceStateData() { return getState() }

def getTimeZone() {
	TimeZone tz
	if(location?.timeZone) { tz = (TimeZone)location?.timeZone }
	else { tz = state.nestTimeZone ? TimeZone.getTimeZone((String)state.nestTimeZone) : null }
	if(!tz) { Logger("getTimeZone: Hub or Nest TimeZone is not found ...", sWARN) }
	return tz
}

String tUnitStr() {
	return "\u00b0${state.tempUnit}".toString()
}

@SuppressWarnings('unused')
void ecoDesc(val) { }

void pauseEvent(String val = sFALSE) {
	if(isStateChange(device, "pauseUpdates", val)) {
		String curData = device.currentState("pauseUpdates")?.value
		Logger("Pause Updates is: (${val}) | Previous State: (${curData})")
		sendEvent(name: 'pauseUpdates', value: val, displayed: false)
	}
}

void nestTypeEvent(String type) {
	String val = device.currentState("nestType")?.value?.toString()
	if(!val.equals(type)) {
		state.nestType=type
		Logger("UPDATED | nestType: (${type}) | Original State: (${val})")
		sendEvent(name: 'nestType', value: type, displayed: true)
	}
}

void sunlightCorrectionEnabledEvent(Boolean sunEn) {
	String newVal = sunEn.toString()
	if(isStateChange(device, "sunlightCorrectionEnabled", newVal)) {
		String val = device.currentState("sunlightCorrectionEnabled")?.value?.toString()
		Logger("SunLight Correction Enabled: (${newVal}) | Previous State: (${val?.capitalize()})")
		sendEvent(name: 'sunlightCorrectionEnabled', value: newVal, displayed: false)
	}
}

void sunlightCorrectionActiveEvent(Boolean sunAct) {
	String newVal = sunAct?.toString()
	if(isStateChange(device, "sunlightCorrectionActive", newVal)) {
		String val = device.currentState("sunlightCorrectionActive")?.value?.toString()
		Logger("SunLight Correction Active: (${newVal}) | Previous State: (${val?.capitalize()})")
		sendEvent(name: 'sunlightCorrectionActive', value: newVal, displayed: false)
	}
}

void timeToTargetEvent(String ttt, String tttTr) {
	//log.debug "timeToTargetEvent($ttt, $tttTr)"
	Boolean opIdle = device.currentState(sNSTOPERSTATE)?.value?.toString() == sOFF
	//log.debug "opIdle: $opIdle"
	Integer nVal; nVal = null
	if(ttt) {
		String ts
		ts = ttt.replaceAll("\\~", sBLK)
		ts = ts.replaceAll("\\>", sBLK)
		nVal = ts.replaceAll("\\<", sBLK).toInteger()
	}
	String trStr; trStr = sBLK
	if(tttTr) {
		trStr = tttTr == "training" ? "\n(Still Training)" : sBLK
	}
	String newVal = ttt ? (nVal == 0 || opIdle ? "System is Idle" : "${nVal} Minutes${trStr}") : "Not Available"
	if(isStateChange(device, "timeToTarget", newVal)) {
		String val = device.currentState("timeToTarget")?.value?.toString()
		Logger("Time to Target: (${newVal}) | Previous State: (${val?.capitalize()})")
		sendEvent(name: 'timeToTarget', value: newVal, displayed: false)
	}
}

void lastCheckinEvent(String checkin, Boolean isOnline) {
	//def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	//tf.setTimeZone(getTimeZone())
	String regex1 = /Z/
	String t0 = checkin.replaceAll(regex1, "-0000")

	//def curConn = t0 ? "${tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))}" : "Not Available"
	String curConnFmt = t0 ? formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0)) : "Not Available"
	//def curConnSeconds = (t0 && curConnFmt != "Not Available") ? getTimeDiffSeconds(curConnFmt) : 3000

	String onlineStat = isOnline ? "online" : "offline"

	if(isStateChange(device, "lastConnection", curConnFmt)) {
		//def lastChk = device.currentState("lastConnection")?.value
		//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000
		// Logger("Last Nest Check-in was: (${curConnFmt}) | Previous Check-in: (${lastChk})")
		sendEvent(name: 'lastConnection', value: curConnFmt, isStateChange: true)
	}
	if(isStateChange(device, "onlineStatus", onlineStat)) {
		String prevOnlineStat = device.currentState("onlineStatus")?.value?.toString()
		Logger("Online Status is: (${onlineStat}) | Previous State: (${prevOnlineStat})")
		sendEvent(name: "onlineStatus", value: onlineStat, descriptionText: "Online Status is: ${onlineStat}", displayed: true, isStateChange: true, state: onlineStat)
	}
}

void lastUpdatedEvent(Boolean sendEvt=false) {
	//def now = new Date()
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	//tf.setTimeZone(getTimeZone())
	String lastDt = getDtNow()
	state.lastUpdatedDt = lastDt
	//state.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		//def lastUpd = device.currentState("lastUpdatedDt")?.value
		// Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastUpdatedDt', value: lastDt, displayed: false, isStateChange: true)
	}
}

void softwareVerEvent(String ver) {
	if(isStateChange(device, "softwareVer", ver)) {
		String verVal = device.currentState("softwareVer")?.value?.toString()
		Logger("Firmware Version: (${ver}) | Previous State: (${verVal})")
		sendEvent(name: 'softwareVer', value: ver, descriptionText: "Firmware Version is now ${ver}", displayed: false, isStateChange: true)
	}
}

void tempUnitEvent(String unit) {
	if(isStateChange(device, "temperatureUnit", unit)) {
		state.tempUnit = unit
		String tmpUnit = device.currentState("temperatureUnit")?.value?.toString()
		Logger("Temperature Unit: (${unit}) | Previous State: (${tmpUnit})")
		sendEvent(name:'temperatureUnit', value: unit, descriptionText: "Temperature Unit is now: '${unit}'", displayed: true, isStateChange: true)
	}
}

// TODO NOT USED
@SuppressWarnings('unused')
def targetTempEvent(Double targetTemp) {
	String temp = device.currentState("targetTemperature")?.value?.toString()
	def rTargetTemp = wantMetric() ? targetTemp.round(1) : targetTemp.round(0).toInteger()
	if(isStateChange(device, "targetTemperature", rTargetTemp.toString())) {
		Logger("targetTemperature is (${rTargetTemp}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name:'targetTemperature', value: rTargetTemp, unit: state.tempUnit, descriptionText: "Target Temperature is ${rTargetTemp}${tUnitStr()}", displayed: false, isStateChange: true)
	}
}

void thermostatSetpointEvent(Double targetTemp) {
	String temp = device.currentState("thermostatSetpoint")?.value?.toString()
	def rTargetTemp = wantMetric() ? targetTemp.round(1) : targetTemp.round(0).toInteger()
	//if(isStateChange(device, "thermostatSetPoint", rTargetTemp.toString())) {
	if (!temp.equals(rTargetTemp.toString())) {
		Logger("thermostatSetPoint Temperature is (${rTargetTemp}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name: 'thermostatSetpoint', value: rTargetTemp, unit: state.tempUnit, descriptionText: "thermostatSetpoint Temperature is ${rTargetTemp}${tUnitStr()}", displayed: false, isStateChange: true)
	}
	updateMinMaxSetpoint('thermostatSetpointMin', 'thermostatSetpointMax', sBLK)
}

void updateMinMaxSetpoint(String smin, String smax, String sTyp) {
	Double curMinTemp, curMaxTemp
	curMinTemp = 0.0D
	curMaxTemp = 100.0D
	Boolean locked = bsIs('tempLockOn')
	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	if(wantMetric()) {
		if(curMinTemp < 9.0D) { curMinTemp = 9.0D }
		if(curMaxTemp > 32.0D) { curMaxTemp = 32.0D }
	} else {
		if(curMinTemp < 50.0D) { curMinTemp = 50.0D }
		if(curMaxTemp > 90.0D) { curMaxTemp = 90.0D }
	}
	sendEvent(name:smin, value: curMinTemp, unit: state.tempUnit, descriptionText: "Thermostat ${sTyp} SetpointMin is ${curMinTemp}${tUnitStr()}", state: sTyp)
	sendEvent(name:smax, value: curMaxTemp, unit: state.tempUnit, descriptionText: "Thermostat ${sTyp} SetpointMax is ${curMaxTemp}${tUnitStr()}", state: sTyp)
}

void temperatureEvent(Double tempVal) {
	def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(isStateChange(device, sTEMP, rTempVal.toString())) {
		def temp = device.currentState(sTEMP)?.value
		Logger("Temperature is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
		sendEvent(name:sTEMP, value: rTempVal, unit: state.tempUnit, descriptionText: "Ambient Temperature is ${rTempVal}${tUnitStr()}", displayed: true, isStateChange: true)
	}
	// checkSafetyTemps()
}

void heatingSetpointEvent(Double tempVal) {
	def temp = device.currentState(sHEATINGSP)?.value
	if(tempVal.toInteger() == 0 || !(Boolean)state.can_heat || (getHvacMode() == sOFF)) {
		if(temp != sBLK) { clearHeatingSetpoint() }
	} else {
		def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
		if(isStateChange(device, sHEATINGSP, rTempVal.toString())) {
			Logger("Heat Setpoint is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
			Boolean disp; disp = false
			String hvacMode = getHvacMode()
			if(hvacMode in [sAUTO, sHEAT]) { disp = true }
			sendEvent(name:sHEATINGSP, value: rTempVal, unit: state.tempUnit, descriptionText: "Heat Setpoint is ${rTempVal}${tUnitStr()}", displayed: disp, isStateChange: true, state: sHEAT)
//			state.allowHeat = true
		}
		updateMinMaxSetpoint('heatingSetpointMin', 'heatingSetpointMax', sHEAT)
	}
}

void coolingSetpointEvent(Double tempVal) {
	def temp = device.currentState(sCOOLINGSP)?.value
	if(tempVal.toInteger() == 0 || !bsIs('can_cool') || (getHvacMode() == sOFF)) {
		if(temp != sBLK) { clearCoolingSetpoint() }
	} else {
		def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
		if(isStateChange(device, sCOOLINGSP, rTempVal.toString())) {
			Logger("Cool Setpoint is (${rTempVal}${tUnitStr()}) | Previous Temp: (${temp}${tUnitStr()})")
			Boolean disp; disp = false
			String hvacMode = getHvacMode()
			if(hvacMode in [sAUTO, sCOOL]) { disp = true }
			sendEvent(name:sCOOLINGSP, value: rTempVal, unit: state.tempUnit, descriptionText: "Cool Setpoint is ${rTempVal}${tUnitStr()}", displayed: disp, isStateChange: true, state: sCOOL)
//			state.allowCool = true
		}
		updateMinMaxSetpoint('coolingSetpointMin', 'coolingSetpointMax', sCOOL)
	}
}

private void hasLeafEvent(Boolean hasLeaf) {
	String lf = hasLeaf ? "On" : "Off"
	if(isStateChange(device, "hasLeaf", lf)) {
		state.hasLeaf = hasLeaf
		String leaf = device.currentState("hasLeaf")?.value?.toString()
		Logger("Leaf is set to (${lf}) | Previous State: (${leaf})")
		sendEvent(name:'hasLeaf', value: lf, descriptionText: "Leaf: ${lf}", displayed: false, isStateChange: true, state: lf)
	}
}

private void humidityEvent(Integer humidity) {
	Integer val = humidity
	if(isStateChange(device, "humidity", val.toString())) {
		def hum = device.currentState("humidity")?.value
		Logger("Humidity is (${val}) | Previous State: (${hum})")
		sendEvent(name:'humidity', value: val, unit: "%", descriptionText: "Humidity is ${humidity}", displayed: false, isStateChange: true)
	}
}

private void etaEvent(String eta) {
	if(isStateChange(device, "etaBegin", eta)) {
		String oeta = device.currentState("etaBegin")?.value?.toString()
		Logger("Eta Begin is (${eta}) | Previous State: (${oeta})")
		sendEvent(name:'etaBegin', value: eta, descriptionText: "Eta is ${eta}", displayed: true, isStateChange: true)
	}
}

private void presenceEvent(String presence) {
	// log.trace "presenceEvent($presence)"
	String val = getPresence()
	String pres = (presence in ["away","auto-away"]) ? "not present" : "present"
	String nestPres = sSs('nestPresence')
	String newNestPres = (pres == "present") ? "home" : ((presence == "auto-away") ? "auto-away" : "away")
	Boolean statePres = (Boolean)state.isPresent
	state.isPresent = !(pres == "not present")
	state.nestPresence = newNestPres
	Boolean presB=isStateChange(device, "presence", pres)
	Boolean nestPresB=isStateChange(device, "nestPresence", newNestPres)
	if(presB || nestPresB || nestPres == sNULL) {
		String chgType; chgType = sBLK
		chgType += presB ? "HE" : sBLK
		chgType += presB && nestPresB ? " | " : sBLK
		chgType += nestPresB ? "Nest" : sBLK
		Logger("${chgType} Presence: ${pres?.capitalize()} | Previous State: ${val?.capitalize()} | State Variable: ${statePres}")
		sendEvent(name: 'presence', value: pres, descriptionText: "Device is: ${pres}", displayed: false, isStateChange: true, state: pres )
		sendEvent(name: 'nestPresence', value: newNestPres, descriptionText: "Nest Presence is: ${newNestPres}", displayed: true, isStateChange: true )
	}
}

void hvacModeEvent(String mode) {
	String hm= sSs('hvac_mode')
	String hvacMode = !hm ? device.currentState("thermostatMode")?.value?.toString() : hm
	String newMode = (mode == sHEATCOOL) ? sAUTO : mode
/*
	if(mode == sECO) {
		if((Boolean)state.can_cool && (Boolean)state.can_heat) { newMode = sAUTO }
		else if((Boolean)state.can_heat) { newMode = sHEAT }
		else if((Boolean)state.can_cool) { newMode = sCOOL }
	}
*/
	state.hvac_mode = newMode
	if(!hvacMode.equals(newMode)) {
		Logger("Hvac Mode is (${newMode?.capitalize()}) | Previous State: (${hvacMode?.capitalize()})")
		sendEvent(name: "thermostatMode", value: newMode, descriptionText: "HVAC mode is ${newMode} mode", displayed: true, isStateChange: true)
	}

	String oldnestmode = sSs('nestHvac_mode')
	//newMode = (mode == sHEATCOOL) ? sAUTO : mode
	state.nestHvac_mode = newMode
	if(!oldnestmode.equals(newMode)) {
		Logger("NEST Hvac Mode is (${newMode?.capitalize()}) | Previous State: (${oldnestmode?.capitalize()})")
		sendEvent(name: "nestThermostatMode", value: newMode, descriptionText: "Nest HVAC mode is ${newMode} mode", displayed: true, isStateChange: true)
	}
}

void hvacPreviousModeEvent(String mode) {
	String newMode = (mode == sHEATCOOL) ? sAUTO : mode
	state.previous_hvac_mode = newMode
	if(mode != sBLK && isStateChange(device, "previousthermostatMode", newMode)) {
		String phm= sSs('previous_hvac_mode')
		String hvacMode = !phm ? device.currentState("previousthermostatMode")?.value?.toString() : phm
		Logger("Hvac Previous Mode is (${newMode?.capitalize()}) | Previous State: (${hvacMode?.capitalize()})")
		sendEvent(name: "previousthermostatMode", value: newMode, descriptionText: "HVAC Previous mode is ${newMode} mode", displayed: true, isStateChange: true)
	}
}

void fanModeEvent(Boolean fanActive) {
	String val = bsIs('has_fan') ? (fanActive ? "on" : sAUTO) : "disabled"
	if(isStateChange(device, "thermostatFanMode", val)) {
		String fanMode = device.currentState("thermostatFanMode")?.value?.toString()
		Logger("Fan Mode: (${val?.capitalize()}) | Previous State: (${fanMode?.capitalize()})")
		sendEvent(name: "thermostatFanMode", value: val, descriptionText: "Fan Mode is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void nestoperatingStateEvent(String opState=sNULL) {
	String nesthvacState = device.currentState(sNSTOPERSTATE)?.value?.toString()
	String operState = opState == sNULL ? nesthvacState : opState
	if(operState == sNULL) { return }
	if(isStateChange(device, sNSTOPERSTATE, operState)) {
		Logger("nestOperatingState is (${operState?.capitalize()}) | Previous State: (${nesthvacState?.capitalize()})")
		sendEvent(name: 'nestThermostatOperatingState', value: operState, descriptionText: "Device is ${operState}")
	}
}

void operatingStateEvent(String opState=sNULL) {
	String operState
	operState = opState == sNULL ? device.currentState(sNSTOPERSTATE)?.value?.toString() : opState
	if(operState == sNULL) { return }

	operState = (operState == sOFF) ? "idle" : operState

	String newoperState
	newoperState = operState
	Boolean fanOn = device.currentState("thermostatFanMode")?.value?.toString() == "on"
	if (fanOn && operState == "idle") { newoperState = "fan only" }

	if(isStateChange(device, sOPERSTATE, newoperState)) {
		String hvacState = device.currentState(sOPERSTATE)?.value?.toString()
		Logger("OperatingState is (${newoperState?.capitalize()}) | Previous State: (${hvacState?.capitalize()})")
		sendEvent(name: 'thermostatOperatingState', value: newoperState, descriptionText: "Device is ${newoperState}", displayed: true, isStateChange: true)
	}
}

void tempLockOnEvent(Boolean isLocked) {
	String slock=isLocked.toString()
	if(isStateChange(device, "tempLockOn", slock)) {
		String curState = device.currentState("tempLockOn")?.value?.toString()
		state.tempLockOn = isLocked
		Logger("Temperature Lock is set to (${slock}) | Previous State: (${curState})")
		sendEvent(name:'tempLockOn', value: slock, descriptionText: "Temperature Lock: ${slock}", displayed: false, isStateChange: true, state: newState)
	}
}

void lockedTempEvent(Double minTemp, Double maxTemp) {
	Double curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
	Double curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	//def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(curMinTemp != minTemp || curMaxTemp != maxTemp) {
		Logger("Temperature Lock Minimum is (${minTemp}) | Previous Temp: (${curMinTemp})")
		Logger("Temperature Lock Maximum is (${maxTemp}) | Previous Temp: (${curMaxTemp})")
		sendEvent(name:'lockedTempMin', value: minTemp, unit: state.tempUnit, descriptionText: "Temperature Lock Minimum is ${minTemp}${state.tempUnit}", displayed: true, isStateChange: true)
		sendEvent(name:'lockedTempMax', value: maxTemp, unit: state.tempUnit, descriptionText: "Temperature Lock Maximum is ${maxTemp}${state.tempUnit}", displayed: true, isStateChange: true)
	}
}
/*
def safetyTempsEvent(safetyTemps) {
	Double curMinTemp = device.currentState("safetyTempMin")?.value?.toDouble()
	Double curMaxTemp = device.currentState("safetyTempMax")?.value?.toDouble()
	Double newMinTemp = safetyTemps && safetyTemps?.min ? safetyTemps.min?.toDouble() : 0.0D
	Double newMaxTemp = safetyTemps && safetyTemps?.max ? safetyTemps.max?.toDouble() : 0.0D

	//def rTempVal = wantMetric() ? tempVal.round(1) : tempVal.round(0).toInteger()
	if(curMinTemp != newMinTemp || curMaxTemp != newMaxTemp) {
		Logger("Safety Temperature Minimum is (${newMinTemp}${state.tempUnit}) | Previous Temp: (${curMinTemp}${state.tempUnit})")
		Logger("Safety Temperature Maximum is (${newMaxTemp}${state.tempUnit}) | Previous Temp: (${curMaxTemp}${state.tempUnit})")
		sendEvent(name:'safetyTempMin', value: newMinTemp, unit: state.tempUnit, descriptionText: "Safety Temperature Minimum is ${newMinTemp}${state.tempUnit}", displayed: true, isStateChange: true)
		sendEvent(name:'safetyTempMax', value: newMaxTemp, unit: state.tempUnit, descriptionText: "Safety Temperature Maximum is ${newMaxTemp}${state.tempUnit}", displayed: true, isStateChange: true)
		// checkSafetyTemps()
	}
}

def checkSafetyTemps() {
	def curMinTemp = device.currentState("safetyTempMin")?.value
	def curMaxTemp = device.currentState("safetyTempMax")?.value
	def curTemp = device.currentState(sTEMP)?.value
	def curRangeStr = device.currentState("safetyTempExceeded")?.value
	Boolean outOfRange = false
	if(curMinTemp && curTemp < curMinTemp ) { outOfRange = true }
	if(curMaxTemp && curTemp > curMaxTemp) { outOfRange = true }
	//log.debug "curMinTemp: $curMinTemp | curMaxTemp: $curMaxTemp | curTemp: $curTemp | outOfRange: $outOfRange | curRangeStr: $curRangeStr"
	// Logger("checkSafetyTemps: (curMinTemp: ${curMinTemp} | curMaxTemp: ${curMaxTemp} | curTemp: ${curTemp} | exceeded: ${outOfRange} | curRangeStr: ${curRangeStr})")
	if(isStateChange(device, "safetyTempExceeded", outOfRange.toString())) {
		sendEvent(name:'safetyTempExceeded', value: outOfRange.toString(), descriptionText: "Safety Temperature ${outOfRange ? "Exceeded" : "OK"} ${curTemp}${state.tempUnit}", displayed: true, isStateChange: true)
		Logger("Safety Temperature Exceeded is (${outOfRange}) | Current Temp: (${curTemp}${state.tempUnit}) | Min: ($curMinTemp${state.tempUnit}) | Max: ($curMaxTemp${state.tempUnit})")
	}
}
*/
void apiStatusEvent(String issueDesc) {
	String newStat = issueDesc
	if(isStateChange(device, "apiStatus", newStat)) {
		String curStat = device.currentState("apiStatus")?.value?.toString()
		Logger("API Status is: (${newStat?.capitalize()}) | Previous State: (${curStat?.capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
	}
}

void emergencyHeatEvent(Boolean emerHeat) {
	String newStat = emerHeat.toString()
	if(isStateChange(device, "usingEmergencyHeat", newStat)) {
		String curStat = device.currentState("usingEmergencyHeat")?.value?.toString()
		state.is_using_emergency_heat = emerHeat
		Logger("Using Emergency Heat is: (${newStat?.capitalize()}) | Previous State: (${curStat?.capitalize()})")
		sendEvent(name: "usingEmergencyHeat", value: newStat, descriptionText: "Using Emergency Heat is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
	}
}

void canHeatCool(Boolean canHeat, Boolean canCool) {
	List<String> supportedThermostatModes = [sOFF, sECO]
	state.can_heat = !canHeat ? false : true
	if(bsIs('can_heat')) { supportedThermostatModes << sHEAT }
	state.can_cool = !canCool ? false : true
	if(bsIs('can_cool')) { supportedThermostatModes << sCOOL }
	state.has_auto = (canCool && canHeat)

	Boolean can_heat = bsIs('can_heat')
	Boolean can_cool = bsIs('can_cool')
	Boolean has_auto = bsIs('has_auto')
	if(can_heat && can_cool) { supportedThermostatModes << sAUTO }
	if(isStateChange(device, "canHeat", can_heat.toString())) {
		sendEvent(name: "canHeat", value: can_heat.toString())
	}
	if(isStateChange(device, "canCool", can_cool.toString())) {
		sendEvent(name: "canCool", value: can_cool.toString())
	}
	if(isStateChange(device, "hasAuto", has_auto.toString())) {
		sendEvent(name: "hasAuto", value: has_auto.toString())
	}
	if(state.supportedThermostatModes != supportedThermostatModes) {
		sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(supportedThermostatModes))
		state.supportedThermostatModes = supportedThermostatModes.collect()
	}

	List<String> nestSupportedThermostatModes = supportedThermostatModes
	//nestSupportedThermostatModes << sECO
	if(state.supportedNestThermostatModes != nestSupportedThermostatModes) {
		sendEvent(name: "supportedNestThermostatModes", value: JsonOutput.toJson(nestSupportedThermostatModes))
		state.supportedNestThermostatModes = nestSupportedThermostatModes.collect()
	}
}

void hasFan(Boolean hasFan) {
	List<String> supportedFanModes
	supportedFanModes = []
	state.has_fan = (hasFan == true)
	if(isStateChange(device, "hasFan", hasFan.toString())) {
		sendEvent(name: "hasFan", value: hasFan.toString())
	}
	if(bsIs('has_fan')) {
		supportedFanModes = [sAUTO,"on"]
	}
	if(state.supportedThermostatFanModes != supportedFanModes) {
		sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(supportedFanModes))
		state.supportedThermostatFanModes = supportedFanModes.collect()
	}
}
/*
private Boolean isEmergencyHeat(val) {
	state.is_using_emergency_heat = !val ? false : true
}
*/
private void clearHeatingSetpoint() {
	sendEvent(name:sHEATINGSP, value: sBLK, descriptionText: "Clear Heating Setpoint", displayed: true )
	sendEvent(name:'heatingSetpointMin', value: sBLK, descriptionText: "Clear Heating SetpointMin", displayed: false )
	sendEvent(name:'heatingSetpointMax', value: sBLK, descriptionText: "Clear Heating SetpointMax", displayed: false )
//	state.allowHeat = false
}

private void clearCoolingSetpoint() {
	sendEvent(name:sCOOLINGSP, value: sBLK, descriptionText: "Clear Cooling Setpoint", displayed: true)
	sendEvent(name:'coolingSetpointMin', value: sBLK, descriptionText: "Clear Cooling SetpointMin", displayed: false)
	sendEvent(name:'coolingSetpointMax', value: sBLK, descriptionText: "Clear Cooling SetpointMax", displayed: false)
//	state.allowCool = false
}

def getCoolTemp() {
	def t0 = device.currentState(sCOOLINGSP)?.value
	return !t0 ? 0 : t0
}

def getHeatTemp() {
	def t0 = device.currentState(sHEATINGSP)?.value
	return !t0 ? 0 : t0
}

@SuppressWarnings('unused')
String getFanMode() {
	def t0 = device.currentState("thermostatFanMode")?.value
	return !t0 ? "unknown" : t0.toString()
}

String getHvacMode() {
	String nhm= sSs('nestHvac_mode')
	return !nhm ? device.currentState("nestThermostatMode")?.value?.toString() : nhm
}

@SuppressWarnings('unused')
String getHvacState() {
	def t0 = device.currentState(sOPERSTATE)?.value
	return !t0 ? "unknown" : t0.toString()
}

String getNestPresence() {
	String np= sSs('nestPresence')
	return !np ? device.currentState("nestPresence")?.value?.toString() : np
}

String getPresence() {
	def t0 = device.currentState("presence")?.value
	return !t0 ? "present" : t0.toString()
}

@SuppressWarnings('unused')
def getTargetTemp() {
	def t0 = device.currentState("targetTemperature")?.value
	return !t0 ? 0 : t0
}

@SuppressWarnings('unused')
def getThermostatSetpoint() {
	def t0 = device.currentState("thermostatSetpoint")?.value
	return !t0 ? 0 : t0
}

def getTemp() {
	def t0 = device.currentState(sTEMP)?.value
	return !t0 ? 0 : t0
}

@SuppressWarnings('unused')
private Integer getHumidity() {
	Integer t0 = device.currentState("humidity")?.value
	return !t0 ? 0 : t0
}

@SuppressWarnings('unused')
private Integer getTempWaitVal() {
	return state.childWaitVal ? (Integer)state.childWaitVal : 3
}

Boolean wantMetric() { return (state.tempUnit == "C") }

def getDevTypeId() { return device?.getDevTypeId() }

/************************************************************************************************
 |							Temperature Setpoint Functions for Buttons							|
 *************************************************************************************************/
@SuppressWarnings('unused')
void heatingSetpointUp() {
	//Logger("heatingSetpointUp()...", sTRACE)
	String operMode = getHvacMode()
	if( operMode in [sHEAT, sECO, sAUTO] ) {
		levelUpDown(1,sHEAT)
	}
}

@SuppressWarnings('unused')
void heatingSetpointDown() {
	//Logger("heatingSetpointDown()...", sTRACE)
	String operMode = getHvacMode()
	if( operMode in [sHEAT,sECO, sAUTO] ) {
		levelUpDown(-1, sHEAT)
	}
}

@SuppressWarnings('unused')
void coolingSetpointUp() {
	//Logger("coolingSetpointUp()...", sTRACE)
	String operMode = getHvacMode()
	if( operMode in [sCOOL,sECO, sAUTO] ) {
		levelUpDown(1, sCOOL)
	}
}

@SuppressWarnings('unused')
void coolingSetpointDown() {
	//Logger("coolingSetpointDown()...", sTRACE)
	String operMode = getHvacMode()
	if( operMode in [sCOOL, sECO, sAUTO] ) {
		levelUpDown(-1, sCOOL)
	}
}

@SuppressWarnings('unused')
void levelUp() {
	levelUpDown(1)
}

@SuppressWarnings('unused')
void levelDown() {
	levelUpDown(-1)
}

void levelUpDown(Integer tempVal, String ichgType = sNULL) {
	//Logger("levelUpDown()...($tempVal | $chgType)", sTRACE)
	String hvacMode = getHvacMode()
	String chgType
	chgType=ichgType
	if(!chgType) { chgType = sBLK }

	if(canChangeTemp()) {
		// From RBOY https://community.smartthings.com/t/multiattributetile-value-control/41651/23
		Boolean upLevel
		Integer lastLvl
		lastLvl= (Integer)state.lastLevelUpDown ?: 0 // If it isn't defined lets baseline it
		//if(!state.lastLevelUpDown) { state.lastLevelUpDown = 0 } // If it isn't defined lets baseline it

		if((lastLvl == 1) && (tempVal == 1)) { upLevel = true } //Last time it was 1 and again it's 1 its increase

		else if((lastLvl == 0) && (tempVal == 0)) { upLevel = false } //Last time it was 0 and again it's 0 then it's decrease

		else if((lastLvl == -1) && (tempVal == -1)) { upLevel = false } //Last time it was -1 and again it's -1 then it's decrease

		else if((tempVal - lastLvl) > 0) { upLevel = true } //If it's increasing then it's up

		else if((tempVal - lastLvl) < 0) { upLevel = false } //If it's decreasing then it's down

		else { log.error "UNDEFINED STATE, CONTACT DEVELOPER. Last level ${lastLvl}, Current level, $tempVal" }

		state.lastLevelUpDown = tempVal // Save it

		def curHeatpoint = device.currentState(sHEATINGSP)?.value
		def curCoolpoint = device.currentState(sCOOLINGSP)?.value
		def curThermSetpoint, targetVal
		curThermSetpoint = device.currentState("thermostatSetpoint")?.value
		targetVal = curThermSetpoint ?: 0.0
		if(hvacMode == sAUTO) {
			if(chgType == sCOOL) {
				targetVal = curCoolpoint
				curThermSetpoint = targetVal
			}
			if(chgType == sHEAT) {
				targetVal = curHeatpoint
				curThermSetpoint = targetVal
			}
		}
		Boolean locked = bsIs('tempLockOn')
		def curMinTemp, curMaxTemp
		curMinTemp = 0.0
		curMaxTemp = 100.0

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
				targetVal = targetVal.toDouble() + 0.5D
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			} else {
				targetVal = targetVal.toDouble() + 1.0D
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			}
		} else {
			//Logger("Reducing by 1 increment")
			if(wantMetric()) {
				targetVal = targetVal.toDouble() - 0.5D
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			} else {
				targetVal = targetVal.toDouble() - 1.0D
				if(targetVal < curMinTemp) { targetVal = curMinTemp }
				if(targetVal > curMaxTemp) { targetVal = curMaxTemp }
			}
		}

		if(targetVal != curThermSetpoint ) {
			pauseEvent(sTRUE)
			switch (hvacMode) {
				case sHEAT:
					if(state.oldHeat == null) { state.oldHeat = curHeatpoint}
					thermostatSetpointEvent(targetVal.toDouble())
					heatingSetpointEvent(targetVal.toDouble())
					//if(!chgType) { chgType = sBLK }
					scheduleChangeSetpoint()
					Logger("Sending changeSetpoint(Temp: ${targetVal})")
					break
				case sCOOL:
					if(state.oldCool == null) { state.oldCool = curCoolpoint}
					thermostatSetpointEvent(targetVal.toDouble())
					coolingSetpointEvent(targetVal.toDouble())
					//if(!chgType) { chgType = sBLK }
					scheduleChangeSetpoint()
					Logger("Sending changeSetpoint(Temp: ${targetVal})")
					break
				case sAUTO:
					if(chgType) {
						switch (chgType) {
							case sCOOL:
								if(state.oldCool == null) { state.oldCool = curCoolpoint }
								coolingSetpointEvent(targetVal.toDouble())
								scheduleChangeSetpoint()
								Logger("Sending changeSetpoint(Temp: ${targetVal})")
								break
							case sHEAT:
								if(state.oldHeat == null) { state.oldHeat = curHeatpoint }
								heatingSetpointEvent(targetVal.toDouble())
								scheduleChangeSetpoint()
								Logger("Sending changeSetpoint(Temp: ${targetVal})")
								break
							default:
								Logger("Unable to Change Temp while in Current Mode: ($chgType}!!!", sWARN)
								break
						}
					} else { Logger("Temp Change without a chgType is not supported!!!", sWARN) }
					break
				default:
					pauseEvent(sFALSE)
					Logger("Unsupported Mode Received: ($hvacMode}!!!", sWARN)
					break
			}
		}
	} else { Logger("levelUpDown: Cannot adjust temperature due to hvacMode ${hvacMode}") }
}

void scheduleChangeSetpoint() {
	if(getLastChangeSetpointSec() > 7) { //getTempWaitVal()
		state.lastChangeSetpointDt = getDtNow()
		runIn( 6, "changeSetpoint", [overwrite: true] )
	}
}

Integer getLastChangeSetpointSec() {
	String dt= sSs('lastChangeSetpointDt')
	return !dt ? 100000 : GetTimeDiffSeconds(dt).toInteger()
}

def getSettingVal(String svar) {
	if(svar == sNULL) { return settings }
	return settings[svar] ?: null
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

String formatDt(Date dt) {
	SimpleDateFormat tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

//Returns time differences is seconds
static Long GetTimeDiffSeconds(String lastDate) {
	if(lastDate?.contains("dtNow")) { return 10000 }
	Date now = new Date()
	Date lastDt = Date.parse("E MMM dd HH:mm:ss z yyyy", lastDate)
	Long start = lastDt.getTime()
	Long stop = now.getTime()
	Long diff = Math.round((stop - start) / 1000L) //
	return diff
}

@SuppressWarnings('unused')
Long getTimeDiffSeconds(String strtDate, String stpDate=sNULL, String methName=sNULL) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		//if(strtDate?.contains("dtNow")) { return 10000 }
		Date now = new Date()
		String stopVal = stpDate ? stpDate : formatDt(now)
		Long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Long diff = Math.round((stop - start) / 1000L) //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return 0L }
}

// Nest does not allow temp changes in off, eco modes
Boolean canChangeTemp() {
	//Logger("canChangeTemp()...", sTRACE)
	if(sSs('nestHvac_mode') != sECO) {
		String hvacMode = getHvacMode()
		switch (hvacMode) {
			case sHEAT:
				return true
				break
			case sCOOL:
				return true
				break
			case sAUTO:
				return true
				break
			default:
				return false
				break
		}
	} else { return false }
}

@SuppressWarnings('unused')
void changeSetpoint() {
	//Logger("changeSetpoint()... ($val)", sTRACE)
	String hvacMode = getHvacMode()
	if(canChangeTemp()) {
		String md; md=sNULL
		def curHeatpoint = getHeatTemp()
		def curCoolpoint = getCoolTemp()
		// Logger("changeSetpoint()... hvacMode: ${hvacMode} curHeatpoint: ${curHeatpoint} curCoolpoint: ${curCoolpoint} oldCool: ${state.oldCool} oldHeat: ${state.oldHeat}", sTRACE)
		switch (hvacMode) {
			case sHEAT:
				state.oldHeat = null
				setHeatingSetpoint(curHeatpoint)
				break
			case sCOOL:
				state.oldCool = null
				setCoolingSetpoint(curCoolpoint)
				break
			case sAUTO:
				if( (state.oldCool != null) && (state.oldHeat == null) ) { md = sCOOL}
				if( (state.oldCool == null) && (state.oldHeat != null) ) { md = sHEAT}
				if( (state.oldCool != null) && (state.oldHeat != null) ) { md = "both"}

				Boolean heatFirst;heatFirst=false
				if(md) {
					if(curHeatpoint >= curCoolpoint) {
						Logger("changeSetpoint: Received an Invalid Temp while in AUTO mode... | Heat: (${curHeatpoint})/Cool: (${curCoolpoint})", sWARN)
					} else {
						if(md == sHEAT) { state.oldHeat = null; setHeatingSetpoint(curHeatpoint) }
						else if(md == sCOOL) { state.oldCool = null; setCoolingSetpoint(curCoolpoint) }
						else if(md == "both") {
							if(curHeatpoint <= state.oldHeat) { heatfirst = true }
							else if(curCoolpoint >= state.oldCool) { heatFirst = false }
								else if(curHeatpoint > state.oldHeat) { heatFirst = false }
									else { heatFirst = true }
							if(heatFirst) {
								state.oldHeat = null
								setHeatingSetpoint(curHeatpoint)
								state.oldCool = null
								setCoolingSetpoint(curCoolpoint)
							} else {
								state.oldCool = null
								setCoolingSetpoint(curCoolpoint)
								state.oldHeat = null
								setHeatingSetpoint(curHeatpoint)
							}
						}
					}
				} else {
					Logger("changeSetpoint: Received Invalid Temp Type... ${md}", sWARN)
					state.oldCool = null
					state.oldHeat = null
				}
				break
			default:
				if(curHeatpoint > curCoolpoint) {
					Logger("changeSetpoint: Received an Invalid Temp while in AUTO mode... ${curHeatpoint} ${curCoolpoint} ${val}", sWARN)
				}
				//thermostatSetpointEvent(temp)
				break
		}
	} else { Logger("changeSetpoint: Cannot adjust Temp Due to hvacMode: ${hvacMode}") }
	pauseEvent(sFALSE)
}

// Nest Only allows F temperatures as #.0 and C temperatures as either #.0 or #.5
void setHeatingSetpoint(temp) {
	setHeatingSetpoint(temp.toDouble())
}

void setHeatingSetpoint(Double reqtemp) {
	// Logger("setHeatingSetpoint()... ($reqtemp)", sTRACE)
	String hvacMode = getHvacMode()
	String tempUnit = sSs('tempUnit')
	def temp
	Boolean canHeat = bsIs('can_heat')
	Boolean locked = bsIs('tempLockOn')
	def curMinTemp, curMaxTemp
	curMinTemp = 0.0
	curMaxTemp = 100.0

	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	// Logger("Heat Temp Received: ${reqtemp} (${tempUnit}) | Temp Locked: ${locked}")
	if(canHeat && sSs('nestHvac_mode') != sECO) {
		switch (tempUnit) {
			case "C":
				temp = Math.round(reqtemp.round(1) * 2) / 2.0f //
				if(curMinTemp < 9.0) { curMinTemp = 9.0 }
				if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Heat Temp: ($temp${tUnitStr()})")
					if(hvacMode == sAUTO) {
						Boolean a=(Boolean)parent.setTargetTempLow(this, tempUnit, temp, virtType())
						heatingSetpointEvent(temp.toDouble())
					}
					if(hvacMode == sHEAT) {
						Boolean a=(Boolean)parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp.toDouble())
						heatingSetpointEvent(temp.toDouble())
					}
				}
				break
			case "F":
				temp = reqtemp.round(0).toInteger()
				if(curMinTemp < 50) { curMinTemp = 50 }
				if(curMaxTemp > 90) { curMaxTemp = 90 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Heat Temp: ($temp${tUnitStr()})")
					if(hvacMode == sAUTO) {
						Boolean a=(Boolean)parent.setTargetTempLow(this, tempUnit, temp, virtType())
						heatingSetpointEvent(temp.toDouble())
					}
					if(hvacMode == sHEAT) {
						Boolean a=(Boolean)parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp.toDouble())
						heatingSetpointEvent(temp.toDouble())
					}
				}
				break
			default:
				Logger("No Temperature Unit Found: ($tempUnit)", sWARN)
				break
		}
	} else {
		Logger("Skipping Heat Change | canHeat: ${canHeat} | hvacMode: ${hvacMode}")
	}
}

void setCoolingSetpoint(temp) {
	setCoolingSetpoint( temp.toDouble())
}

void setCoolingSetpoint(Double reqtemp) {
	// Logger("setCoolingSetpoint()... ($reqtemp)", sTRACE)
	String hvacMode = getHvacMode()
	String tempUnit = sSs('tempUnit')
	def temp
	Boolean canCool = bsIs('can_cool')
	Boolean locked = bsIs('tempLockOn')

	def curMinTemp, curMaxTemp
	curMinTemp = 0.0
	curMaxTemp = 100.0

	if(locked) {
		curMinTemp = device.currentState("lockedTempMin")?.value?.toDouble()
		curMaxTemp = device.currentState("lockedTempMax")?.value?.toDouble()
	}
	// Logger("Cool Temp Received: (${reqtemp}${tempUnit}) | Temp Locked: ${locked}")
	if(canCool && (String)state.nestHvac_mode != sECO) {
		switch (tempUnit) {
			case "C":
				temp = Math.round(reqtemp.round(1) * 2) / 2.0f //
				if(curMinTemp < 9.0) { curMinTemp = 9.0 }
				if(curMaxTemp > 32.0) { curMaxTemp = 32.0 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Cool Temp: ($temp${tUnitStr()})")
					if(hvacMode == sAUTO) {
						Boolean a=(Boolean)parent.setTargetTempHigh(this, tempUnit, temp, virtType())
						coolingSetpointEvent(temp.toDouble())
					}
					if(hvacMode == sCOOL) {
						Boolean a=(Boolean)parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp.toDouble())
						coolingSetpointEvent(temp.toDouble())
					}
				}
				break

			case "F":
				temp = reqtemp.round(0).toInteger()
				if(curMinTemp < 50) { curMinTemp = 50 }
				if(curMaxTemp > 90) { curMaxTemp = 90 }
				if(temp) {
					if(temp < curMinTemp) { temp = curMinTemp }
					if(temp > curMaxTemp) { temp = curMaxTemp }
					Logger("Sending Cool Temp: ($temp${tUnitStr()})")
					if(hvacMode == sAUTO) {
						Boolean a=(Boolean)parent.setTargetTempHigh(this, tempUnit, temp, virtType())
						coolingSetpointEvent(temp.toDouble())
					}
					if(hvacMode == sCOOL) {
						Boolean a=(Boolean)parent.setTargetTemp(this, tempUnit, temp, hvacMode, virtType())
						thermostatSetpointEvent(temp.toDouble())
						coolingSetpointEvent(temp.toDouble())
					}
				}
				break
			default:
				Logger("No Temperature Unit Found: ($tempUnit)", sWARN)
				break
		}
	} else {
		Logger("Skipping Cool Change | canCool: ${canCool} | hvacMode: ${hvacMode}")
	}
}

/************************************************************************************************
 |									NEST PRESENCE FUNCTIONS										|
 *************************************************************************************************/
@SuppressWarnings('unused')
void setPresence() {
	Logger("setPresence()...", sTRACE)
	String pres = getNestPresence()
	Logger("Current Nest Presence: ${pres}", sTRACE)
	if(pres in ["auto-away","away"]) {
		if((Boolean)parent.setStructureAway(this, sFALSE, virtType())) { presenceEvent("home") }
	}
	else if(pres == "home") {
		if((Boolean)parent.setStructureAway(this, sTRUE, virtType())) { presenceEvent("away") }
	}
}

// backward compatibility for previous nest thermostat (and rule machine)
void away() {
	Logger("away()...", sTRACE)
	setAway()
}

// backward compatibility for previous nest thermostat (and rule machine)
void present() {
	Logger("present()...", sTRACE)
	setHome()
}

void setAway() {
	Logger("setAway()...", sTRACE)
	if((Boolean)parent.setStructureAway(this, sTRUE, virtType())) { presenceEvent("away") }
}

void setHome() {
	Logger("setHome()...", sTRACE)
	if((Boolean)parent.setStructureAway(this, sFALSE, virtType()) ) { presenceEvent("home") }
}

/*
def setNestEta(tripId, begin, end){
	Logger("setNestEta()...", sTRACE)
	parent?.setEtaState(this, ["trip_id": "${tripId}", "estimated_arrival_window_begin": "${begin}", "estimated_arrival_window_end": "${end}" ], virtType() )
}

def cancelNestEta(tripId){
	Logger("cancelNestEta()...", sTRACE)
	parent?.cancelEtaState(this, "${tripId}", virtType() )
}
*/

/************************************************************************************************
 |										HVAC MODE FUNCTIONS										|
 ************************************************************************************************/

private List<String> getHvacModes() {
	//Logger("Building Modes list")
	List<String> modesList = [sOFF]
	if( bsIs('can_heat') ) { modesList.push(sHEAT) }
	if( bsIs('can_cool') ) { modesList.push(sCOOL) }
	if( bsIs('can_heat') && bsIs('can_cool')) { modesList.push(sAUTO) }
	modesList.push(sECO)
	Logger("Modes = ${modesList}")
	return modesList
}

/*
def changeMode() {
	//Logger("changeMode..")
	String currentMode = getHvacMode()
	def lastTriedMode = currentMode ?: sOFF
	def modeOrder = getHvacModes()
	def next = { modeOrder[modeOrder.indexOf(it) + 1] ?: modeOrder[0] }
	def nextMode = next(lastTriedMode)
	Logger("changeMode() | currentMode: ${currentMode} | lastModeTried: ${lastTriedMode} | nextMode: ${nextMode}", sTRACE)
	setHvacMode(nextMode)
}
*/

@SuppressWarnings('unused')
def setHvacMode(nextMode) {
	Logger("setHvacMode(${nextMode})")
	if(nextMode in getHvacModes()) {
		state.lastTriedMode = nextMode
		"$nextMode"()
	} else {
		Logger("Invalid Mode '$nextMode'")
	}
}

void off() {
	Logger("off()...", sTRACE)
	pauseEvent(sTRUE)
	hvacModeEvent(sOFF)
	if((Boolean)parent.setHvacMode(this, sOFF, virtType())) {
		pauseEvent(sFALSE)
	}
}

void heat() {
	Logger("heat()...", sTRACE)
	pauseEvent(sTRUE)
	hvacModeEvent(sHEAT)
	if((Boolean)parent.setHvacMode(this, sHEAT, virtType())) {
		pauseEvent(sFALSE)
	}
}

void emergencyHeat() {
	Logger("emergencyHeat()...", sTRACE)
	Logger("Emergency Heat setting not allowed", sWARN)
}

void cool() {
	Logger("cool()...", sTRACE)
	pauseEvent(sTRUE)
	hvacModeEvent(sCOOL)
	if((Boolean)parent.setHvacMode(this, sCOOL, virtType())) {
		pauseEvent(sFALSE)
	}
}

void auto() {
	Logger("auto()...", sTRACE)
	pauseEvent(sTRUE)
	hvacModeEvent(sAUTO)
	if((Boolean)parent.setHvacMode(this, sHEATCOOL, virtType())) {
		pauseEvent(sFALSE)
	}
}

void eco() {
	Logger("eco()...", sTRACE)
	pauseEvent(sTRUE)
	hvacModeEvent(sECO)
	if((Boolean)parent.setHvacMode(this, sECO, virtType())) {
		pauseEvent(sFALSE)
	}
}

void setThermostatMode(modeStr) {
	Logger("setThermostatMode()...", sTRACE)
	switch(modeStr) {
		case sAUTO:
			auto()
			break
		case sHEAT:
			heat()
			break
		case sCOOL:
			cool()
			break
		case sECO:
			eco()
			break
		case sOFF:
			off()
			break
		case "emergency heat":
			emergencyHeat()
			break
		default:
			Logger("setThermostatMode Received an Invalid Request: ${modeStr}", sWARN)
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
		setThermostatFanMode(sAUTO)
	} else {
		setThermostatFanMode("on")
	}
}
*/

void fanOn() {
	try {
		Logger("fanOn()...", sTRACE)
		if(bsIs('has_fan')) {
			if((Boolean)parent.setFanMode(this, true, virtType()) ) { fanModeEvent(true) }
		} else { Logger("Error setting fanOn", "error") }
	}
	catch (ex) {
		log.error "fanOn Exception: ${ex?.message}"
	}
}

/*
// non standard by Hubitat Capabilities Thermostat Fan Mode
void fanOff() {
	Logger("fanOff()...", sTRACE)
	fanAuto()
}
*/

void fanCirculate() {
	Logger("fanCirculate()...", sTRACE)
	fanOn()
}

void fanAuto() {
	try {
		Logger("fanAuto()...", sTRACE)
		if(bsIs('has_fan')) {
			if((Boolean)parent.setFanMode(this,false, virtType()) ) { fanModeEvent(false) }
		} else { Logger("Error setting fanAuto", "error") }
	}
	catch (ex) {
		log.error "fanAuto Exception: ${ex?.message}"
	}
}

void setThermostatFanMode(String fanModeStr) {
	Logger("setThermostatFanMode($fanModeStr)...", sTRACE)
	switch(fanModeStr) {
		case sAUTO:
			fanAuto()
			break
		case "on":
			fanOn()
			break
		case "circulate":
			fanCirculate()
			break
/*		case sOFF:	// non standard by Hubitat Capabilities Thermostat Fan Mode
			fanOff()
			break
*/
		default:
			Logger("setThermostatFanMode Received an Invalid Request: ${fanModeStr}", sWARN)
			break
	}
}

@SuppressWarnings('unused')
void setSchedule(obj) {
	Logger("setSchedule...", sTRACE)
}

/**************************************************************************
 |						LOGGING FUNCTIONS								  |
 **************************************************************************/

static String lastN(String input, Integer n) {
	return n > input?.size() ? input : input[-n..-1]
}

void Logger(GString msg, String logType = "debug") {
	Logger(msg.toString(), logType)
}

void Logger(String msg, String logType = "debug") {
	if(!(Boolean)logEnable || !msg) { return }
	if((Boolean)state.enRemDiagLogging == null) {
		state.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if((Boolean)state.enRemDiagLogging == null) {
			state.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state.enRemDiagLogging}"
	}
	if((Boolean)state.enRemDiagLogging) {
		String smsg = "${device.displayName} (v${devVer()}) | ${msg}".toString()
		String theId = lastN(device.getId().toString(),5)
		Boolean a=parent.saveLogtoRemDiagStore(smsg, logType, "Thermostat-${theId}".toString())
	} else {
		switch (logType) {
			case sTRACE:
				log.trace msg
				break
			case "debug":
				log.debug msg
				break
			case "info":
				log.info msg
				break
			case sWARN:
				log.warn msg
				break
			case "error":
				log.error msg
				break
			default:
				log.debug msg
				break
		}
	}
}

static String getObjType(obj) {
	if(obj instanceof String) {return "String"}
	else if(obj instanceof GString) {return "GString"}
	else if(obj instanceof Map) {return "Map"}
	else if(obj instanceof List) {return "List"}
	else if(obj instanceof ArrayList) {return "ArrayList"}
	else if(obj instanceof Integer) {return "Integer"}
	else if(obj instanceof BigInteger) {return "BigInteger"}
	else if(obj instanceof Long) {return "Long"}
	else if(obj instanceof Boolean) {return "Boolean"}
	else if(obj instanceof BigDecimal) {return "BigDecimal"}
	else if(obj instanceof Float) {return "Float"}
	else if(obj instanceof Byte) {return "Byte"}
	else { return "unknown"}
}

@CompileStatic
private static Boolean bIs(Map m,String v){ (Boolean)m[v] }

/** m.string  */
@CompileStatic
private static String sMs(Map m,String v){ (String)m[v] }

/** state.v  */
private String sSs(String v){ (String)state[v] }

private Boolean bsIs(String v){ (Boolean)state[v] }

private Long wnow(){ return (Long)now() }
