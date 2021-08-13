/**
 *  Nest Protect
 *	Copyright (C) 2018, 2019 Anthony Santilli.
 *	Author: Anthony Santilli (@tonesto7), Eric Schott (@imnotbob)
 *  Modified: 05/10/2020
 */

import java.text.SimpleDateFormat

preferences { }

String devVer() { return "2.0.3" }

metadata {
	definition (name: "Nest Protect", author: "Anthony S.", namespace: "tonesto7", importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/drivers/nstProtect.groovy") {
		//capability "Polling"
		capability "Actuator"
		capability "Sensor"
		capability "Battery"
		capability "Smoke Detector"
		capability "Power Source"
		capability "Carbon Monoxide Detector"
		capability "Refresh"

		command "refresh"
		command "poll"

		attribute "alarmState", "string"
		attribute "batteryState", "string"
		attribute "battery", "string"
		attribute "uiColor", "string"
		attribute "softwareVer", "string"
		attribute "lastConnection", "string"
	//	attribute "lastUpdateDt", "string"
		attribute "lastTested", "string"
		attribute "isTesting", "string"
		attribute "apiStatus", "string"
		attribute "onlineStatus", "string"
		attribute "carbonMonoxide", "string"
		attribute "smoke", "string"
		attribute "nestCarbonMonoxide", "string"
		attribute "powerSourceNest", "string"
		attribute "nestSmoke", "string"
	}
	preferences {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
	}
}

void logsOff(){
	log.warn "${device?.displayName} debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
		state.updatedLastRanAt = now()
		state?.isInstalled = true
		log.warn "debug logging is: ${logEnable} | description logging is: ${txtEnable}"
		if (logEnable) runIn(1800,logsOff)
	} else {
		Logger("initialize(): Ran within last 2 seconds - SKIPPING")
	}
	state.remove("enRemDiagLogging")
}

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	runIn(5, "initialize", [overwrite: true])
}

void updated() {
	log.trace "Device Updated: (${device?.displayName})..."
	runIn(5, "initialize", [overwrite: true])
}

void uninstalled() {
	log.trace "Device Removed: (${device?.displayName})..."
}

void verifyDataAttr() {
	if(!device?.getDataValue("manufacturer")) {
		updateDataValue("manufacturer", "Nest")
	}
	if(!device?.getDataValue("model")) {
		updateDataValue("model", device?.name as String)
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
	//log.trace("polling parent...")
	parent?.refresh(this)
}

void refresh() {
	poll()
}

void generateEvent(eventData) {
	//log.trace("processEvent Parsing data ${eventData}")
	try {
		if(eventData) {
			def results = eventData?.data
			state.nestTimeZone = eventData?.tz ?: null
			state?.showProtActEvts = eventData?.showProtActEvts ? true : false
			carbonSmokeStateEvent(results?.co_alarm_state.toString(),results?.smoke_alarm_state.toString())
			if(!results?.last_connection) { lastCheckinEvent(null, null) }
			else { lastCheckinEvent(results?.last_connection, results?.is_online.toString()) }
			lastTestedEvent(results?.last_manual_test_time)
			apiStatusEvent(eventData?.apiIssues)
			//onlineStatusEvent(results?.is_online.toString())
			batteryStateEvent(results?.battery_health.toString())
			testingStateEvent(results?.is_manual_test_active.toString())
			uiColorEvent(results?.ui_color_state.toString())
			softwareVerEvent(results?.software_version.toString())
			determinePwrSrc()
			lastUpdatedEvent(false)
		}
		return
	}
	catch (ex) {
		log.error "generateEvent Exception: ${ex?.message}"
	}
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

def getSettingVal(var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

String formatDt(Date dt) {
	def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

Long getTimeDiffSeconds(String strtDate, String stpDate=null, String methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	try {
		if((strtDate && !stpDate) || (strtDate && stpDate)) {
			Date now = new Date()
			String stopVal = stpDate ? stpDate : formatDt(now)
			Long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
			Long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
			Long diff = (stop - start) / 1000L
			return diff
		} else { return null }
	} catch (ex) {
		log.warn "getTimeDiffSeconds error: Unable to parse datetime..."
	}
}

Integer getStateSize()      { return state?.toString().length() }
Integer getStateSizePerc()  { return (Integer) Math.round((stateSize/100000)*100) }
def getDevTypeId() 		{ return device?.getDevTypeId() }

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDeviceStateData() {
	return getState()
}

def getTimeZone() {
	def tz = null
	if (location?.timeZone) { tz = location?.timeZone }
	else { tz = state?.nestTimeZone ? TimeZone.getTimeZone(state?.nestTimeZone) : null }
	if(!tz) { log.warn "getTimeZone: Hub or Nest TimeZone is not found ..." }
	return tz
}

void lastCheckinEvent(checkin, isOnline) {
	//Logger("checkin: ${checkin}, isOnline: ${isOnline}", "debug")
	def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	def regex1 = /Z/
	String t0 = checkin.replaceAll(regex1, "-0000")
	tf.setTimeZone(getTimeZone())

	String prevOnlineStat = device.currentState("onlineStatus")?.value

	//def curConn = t0 ? "${tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))}" : "Not Available"
	String curConnFmt = t0 ? formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0)) : "Not Available"
	//Logger("curConn: ${curConn}   curConnFmt: ${curConnFmt}  timeDiff: ${getTimeDiffSeconds(curConnFmt)}", "debug")
	//def curConnSeconds = (t0 && curConnFmt != "Not Available") ? getTimeDiffSeconds(curConnFmt) : 3000

	String onlineStat = isOnline.toString() == "true" ? "online" : "offline"

	if(isStateChange(device, "lastConnection", curConnFmt)) {
		String lastChk = device.currentState("lastConnection")?.value
		Long lastConnSeconds = lastChk ? getTimeDiffSeconds(lastChk) : 9000L   // try not to disrupt running average for pwr determination

		// Logger("Last Nest Check-in was: (${curConnFmt}) | Original State: (${lastChk})")
		sendEvent(name: 'lastConnection', value: curConnFmt, displayed: state?.showProtActEvts, isStateChange: true)
		if(lastConnSeconds >= 0L && onlineStat == "online") { addCheckinTime(lastConnSeconds) }
	}

	state.onlineStatus = onlineStat
	if(isStateChange(device, "onlineStatus", onlineStat)) {
		Logger("Online Status is: (${onlineStat}) | Original State: (${prevOnlineStat})")
		sendEvent(name: "onlineStatus", value: onlineStat, descriptionText: "Online Status is: ${onlineStat}", displayed: state?.showProtActEvts, isStateChange: true, state: onlineStat)
	}
}

void addCheckinTime(val) {
	List list = state?.checkinTimeList ?: []
	Integer listSize = 12
	if(list?.size() < listSize) {
		list.push(val)
	}
	else if(list?.size() > listSize) {
		Integer nSz = (list?.size()-listSize) + 1
		List nList = list?.drop(nSz)
		nList?.push(val)
		list = nList
	}
	else if(list?.size() == listSize) {
		List nList = list?.drop(1)
		nList?.push(val)
		list = nList
	}
	if(list) { state.checkinTimeList = list }
}

void determinePwrSrc() {
	if(!state?.checkinTimeList) { state?.checkinTimeList = [] }
	def checkins = state?.checkinTimeList
	def checkinAvg = checkins?.size() ? ( checkins?.sum()?.div(checkins?.size()))?.toDouble()?.round(0).toInteger() : null //
	if(checkins?.size() > 7) {
		if(checkinAvg && checkinAvg < 10000) {
			powerTypeEvent(true)
		} else { powerTypeEvent() }
	}
	//log.debug "checkins: $checkins | Avg: $checkinAvg"
}

void powerTypeEvent(Boolean wired=false) {
	String curVal = device.currentState("powerSourceNest")?.value
	String newValSt = wired == true ? "wired" : "battery"
	String newVal = wired == true ? "mains" : "battery"
	if(isStateChange(device, "powerSource", newVal) || isStateChange(device, "powerSourceNest", newValSt)) {
		Logger("The Device's Power Source is: (${newVal}) | Original State: (${curVal})")
		sendEvent(name: 'powerSource', value: newVal, displayed: true, isStateChange: true)
		sendEvent(name: 'powerSourceNest', value: newValSt, displayed: true, isStateChange: true)
	}
}

void lastTestedEvent(dt) {
	String lastTstVal = device.currentState("lastTested")?.value
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	def regex1 = /Z/
	String t0 = dt ? dt.replaceAll(regex1, "-0000") : dt
	String lastTest = !t0 ? "No Test Recorded" : tf?.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))
	if(isStateChange(device, "lastTested", lastTest)) {
		Logger("Last Manual Test was: (${lastTest}) | Original State: (${lastTstVal})")
		sendEvent(name: 'lastTested', value: lastTest, displayed: true, isStateChange: true)
	}
}

void softwareVerEvent(String ver) {
	String verVal = device.currentState("softwareVer")?.value
	if(isStateChange(device, "softwareVer", ver)) {
		Logger("Firmware Version: (${ver}) | Original State: (${verVal})")
		sendEvent(name: 'softwareVer', value: ver, descriptionText: "Firmware Version is now v${ver}", displayed: false)
	}
}

void apiStatusEvent(issueDesc) {
	String curStat = device.currentState("apiStatus")?.value
	String newStat = issueDesc
	if(isStateChange(device, "apiStatus", newStat)) {
		Logger("API Status is: (${newStat.capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
	}
}

void lastUpdatedEvent(Boolean sendEvt=false) {
	//def now = new Date()
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	//tf.setTimeZone(getTimeZone())
	String lastDt = getDtNow()	//"${tf?.format(now)}"
	state.lastUpdatedDt = lastDt
	//state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		//def lastUpd = device.currentState("lastUpdatedDt")?.value
		// Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastUpdatedDt', value: lastDt, displayed: false, isStateChange: true)
	}
}

void uiColorEvent(color) {
	def colorVal = device.currentState("uiColor")?.value
	if(isStateChange(device, "uiColor", color.toString())) {
		Logger("UI Color is: (${color}) | Original State: (${colorVal})")
		sendEvent(name:'uiColor', value: color.toString(), displayed: false, isStateChange: true)
	}
}

void batteryStateEvent(String batt) {
	Integer stbattery = (batt == "replace") ? 5 : 100
	String battVal = device.currentState("batteryState")?.value
	def stbattVal = device.currentState("battery")?.value
	if(isStateChange(device, "batteryState", batt) || !stbattVal) {
		Logger("Battery is: ${batt} | Original State: (${battVal})")
		sendEvent(name:'batteryState', value: batt, descriptionText: "Nest Battery status is: ${batt}", displayed: true, isStateChange: true)
		sendEvent(name:'battery', value: stbattery, descriptionText: "Battery is: ${stbattery}", displayed: true, isStateChange: true)
	}
}

void testingStateEvent(test) {
	String testVal = device.currentState("isTesting")?.value
	if(isStateChange(device, "isTesting", test.toString())) {
		Logger("Testing State: (${test}) | Original State: (${testVal})")
		sendEvent(name:'isTesting', value: test, descriptionText: "Manual test: ${test}", displayed: true, isStateChange: true)
	}
}

void carbonSmokeStateEvent(coState, smokeState) {
	//values in Hubitat are tested, clear, detected
	//values from nest are ok, warning, emergency
	String carbonVal = device.currentState("nestCarbonMonoxide")?.value
	String smokeVal = device.currentState("nestSmoke")?.value
	String testVal = device.currentState("isTesting")?.value

	String alarmStateHE = "ok"
	String smokeValStr = "clear"
	String carbonValStr = "clear"

	if (smokeState == "emergency" || smokeState == "warning") {
		alarmStateHE = smokeState == "emergency" ? "smoke-emergency" : "smoke-warning"
		smokeValStr = "detected"
	}
	if (coState == "emergency" || coState == "warning") {
		alarmStateHE = coState == "emergency" ? "co-emergency" : "co-warning"
		carbonValStr = "detected"
	}
	if(isStateChange(device, "nestSmoke", smokeState.toString())) {
		Logger("Nest Smoke State is: (${smokeState.toString().toUpperCase()}) | Original State: (${smokeVal.toString().toUpperCase()})")
		sendEvent( name: 'nestSmoke', value: smokeState, descriptionText: "Nest Smoke Alarm: ${smokeState}", type: "physical", displayed: true, isStateChange: true )
		sendEvent( name: 'smoke', value: smokeValStr, descriptionText: "Smoke Alarm: ${smokeState} Testing: ${testVal}", type: "physical", displayed: true, isStateChange: true )
	}
	if(isStateChange(device, "nestCarbonMonoxide", coState.toString())) {
		Logger("Nest CO State is : (${coState.toString().toUpperCase()}) | Original State: (${carbonVal.toString().toUpperCase()})")
		sendEvent( name: 'nestCarbonMonoxide', value: coState, descriptionText: "Nest CO Alarm: ${coState}", type: "physical", displayed: true, isStateChange: true )
		sendEvent( name: 'carbonMonoxide', value: carbonValStr, descriptionText: "CO Alarm: ${coState} Testing: ${testVal}", type: "physical", displayed: true, isStateChange: true )
	}

	//log.info "alarmState: ${alarmStateHE} (Nest Smoke: ${smokeState.toString().capitalize()} | Nest CarbonMonoxide: ${coState.toString().capitalize()})"
	if(isStateChange(device, "alarmState", alarmStateHE)) {
		sendEvent( name: 'alarmState', value: alarmStateHE, descriptionText: "Alarm: ${alarmStateHE} (Smoke/CO: ${smokeState}/${coState})", type: "physical", displayed: state?.showProtActEvts )
	}
}

/************************************************************************************************
 |										LOGGING FUNCTIONS										|
 *************************************************************************************************/

String lastN(String input, n) {
	return n > input?.size() ? input : input[-n..-1]
}

void Logger(String msg, String logType = "debug") {
	if(!logEnable || !msg) { return }
	String smsg = "${device.displayName} (v${devVer()}) | ${msg}"
	if(state?.enRemDiagLogging == null) {
		state.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if(state?.enRemDiagLogging == null) {
			state.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state?.enRemDiagLogging}"
	}
        if(state?.enRemDiagLogging) {
		String theId = lastN(device.getId().toString(),5)
                parent.saveLogtoRemDiagStore(smsg, logType, "Protect-${theId}")
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

//This will Print logs from the parent app when added to parent method that the child calls
def log(message, level = "trace") {
	Logger("PARENT_Log>> " + message, level)
	return null // always child interface call with a return value
}
