/**
 *  Nest Presence
 *	Copyright (C) 2018, 2019 Anthony Santilli.
 *	Author: Anthony Santilli (@tonesto7), Eric Schott (@imnotbob)
 *  Modified: 05/10/2020
 */

import java.text.SimpleDateFormat

String devVer() { return "2.0.3" }

metadata {
	definition (name: "Nest Presence", namespace: "tonesto7", author: "Anthony S.", importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/drivers/nstPresence.groovy") {
		capability "Actuator"
		capability "Presence Sensor"
		capability "Sensor"
		capability "Refresh"

		command "setPresence"
		command "refresh"
		command "setHome"
		command "setAway"
		command "setNestEta", ["string", "string", "string"]
		command "cancelNestEta", ["string"]

		attribute "etaBegin", "string"
		attribute "lastConnection", "string"
		attribute "apiStatus", "string"
		attribute "nestPresence", "string"
		attribute "peakStart", "string"
		attribute "peakEnd", "string"
		attribute "securityState", "string"
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

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	runIn(5, "initialize", [overwrite: true])
}

void initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
		state.updatedLastRanAt = now()
		state?.isInstalled = true
		log.warn "debug logging is: ${logEnable} | description logging is: ${txtEnable}"
		if (logEnable) runIn(1800,logsOff)
	} else {
		log.trace "initialize(): Ran within last 2 seconds - SKIPPING"
	}
	state.remove("enRemDiagLogging")
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

def configure() { }

def poll() {
	//log.trace("Polling parent...")
	parent?.refresh(this)
}

void refresh() {
	poll()
}

void generateEvent(eventData) {
	// log.trace("eventData ${eventData}")
	try {
		if(eventData) {
			state.nestTimeZone = eventData?.tz ?: null
			presenceEvent(eventData?.pres)
			etaEvent(eventData?.etaBegin)
			securityStateEvent(eventData?.secState)
			peakEvent(eventData?.peakStart, eventData?.peakEnd)
			apiStatusEvent(eventData?.apiIssues)
			lastConnectionEvent(eventData?.lastStrDataUpd)
			lastUpdatedEvent()
		}
		return
	}
	catch (ex) {
		log.error "generateEvent Exception: ${ex.message}"
	}
}

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDevTypeId() { return device?.getDevTypeId() }

def getDeviceStateData() {
	return getState()
}

def getTimeZone() {
	def tz = null
	if (location?.timeZone) { tz = location?.timeZone }
	else { tz = state?.nestTimeZone ? TimeZone.getTimeZone(state?.nestTimeZone) : null }
	if(!tz) { Logger("getTimeZone: Hub or Nest TimeZone is not found...", "warn") }
	return tz
}

void lastUpdatedEvent(Boolean sendEvt=false) {
	//def now = new Date()
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	//tf.setTimeZone(getTimeZone())
	String lastUpd = device.currentState("lastUpdatedDt")?.value
	String lastDt = getDtNow() //"${tf?.format(now)}"
	state.lastUpdatedDt = lastDt
	//state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastUpdatedDt', value: lastDt, displayed: false, isStateChange: true)
	}
}

void lastConnectionEvent(String checkin, Boolean sendEvt=false) {
	def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	def regex1 = /Z/
	checkin = checkin.replaceAll(regex1, "-0000")
	tf.setTimeZone(getTimeZone())

	//def curConn = checkin ? "${tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", checkin))}" : "Not Available"
	String curConnFmt = checkin ? formatDt(Date.parse("E MMM dd HH:mm:ss z yyyy", checkin)) : "Not Available"
	state?.lastConnection = curConnFmt

	if(sendEvt) {
		//def curConnSeconds = (checkin && curConnFmt != "Not Available") ? getTimeDiffSeconds(curConnFmt) : 3000

		String lastChk = device.currentState("lastConnection")?.value
		//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000

		 if(isStateChange(device, "lastConnection", curConnFmt)) {
			Logger("Last Nest Check-in was: (${curConnFmt}) | Previous Check-in: (${lastChk})")
			sendEvent(name: 'lastConnection', value: curConnFmt?.toString(), isStateChange: true)
		}
	}
}

void presenceEvent(presence) {
	String val = device.currentState("presence")?.value
	String pres = (presence == "home") ? "present" : "not present"
	String nestPres = !device.currentState("nestPresence") ? null : device.currentState("nestPresence")?.value.toString()
	String newNestPres = (presence == "home") ? "home" : ((presence == "auto-away") ? "auto-away" : "away")
	// def statePres = state?.present
	// state?.present = (pres == "present") ? true : false
	// state?.nestPresence = newNestPres
	if(isStateChange(device, "presence", pres) || isStateChange(device, "nestPresence", newNestPres) || !nestPres) {
		Logger("Presence is: ${pres} | Previous State: ${val}")
		sendEvent(name: 'nestPresence', value: newNestPres, descriptionText: "Nest Presence is: ${newNestPres}", displayed: true, isStateChange: true )
		sendEvent(name: 'presence', value: pres, descriptionText: "Device is: ${pres}", displayed: true, isStateChange: true )
	}
}

void etaEvent(String eta) {
	String oeta = device.currentState("etaBegin")?.value
	if(isStateChange(device, "etaBegin", eta)) {
		Logger("Eta Begin is (${eta}) | Previous State: (${oeta})")
		sendEvent(name:'etaBegin', value: eta, descriptionText: "Eta is ${eta}", displayed: true, isStateChange: true)
	}
}

void securityStateEvent(sec) {
	String val = ""
	String oldState = device.currentState("securityState")?.value
	if(sec) { val = sec }
	if(isStateChange(device, "securityState", val)) {
		Logger("Security State is (${val}) | Previous State: (${oldState})")
		sendEvent(name: "securityState", value: val, descriptionText: "Location Security State is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void peakEvent(start, end) {
	if(start && end) {
		def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
		tf.setTimeZone(getTimeZone())

		def regex1 = /Z/
		String tstart = start.replaceAll(regex1, "-0000")
		String tend = end.replaceAll(regex1, "-0000")
		String startFmt = tstart ? formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", tstart)) : "Not Available"
		String endFmt = tend ? formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", tend)) : "Not Available"

			String lastChk = device.currentState("peakStart")?.value

			 if(isStateChange(device, "peakStart", startFmt)) {
				Logger("Peak Start: (${startFmt}) | Previous: (${lastChk})")
				sendEvent(name: 'peakStart', value: startFmt, isStateChange: true)
			}

			lastChk = device.currentState("peakEnd")?.value
			//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000

			 if(isStateChange(device, "peakEnd", endFmt)) {
				Logger("Peak End: (${endFmt}) | Previous: (${lastChk})")
				sendEvent(name: 'peakEnd', value: endFmt, isStateChange: true)
			}
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

def getNestPresence() {
	return !device.currentState("nestPresence") ? "home" : device.currentState("nestPresence")?.value.toString()
}

def getPresence() {
	return !device.currentState("presence") ? "present" : device.currentState("presence").value.toString()
}

/************************************************************************************************
 |									NEST PRESENCE FUNCTIONS										|
 *************************************************************************************************/
void setPresence() {
	try {
		log.trace "setPresence()..."
		def pres = getNestPresence()
		// log.trace "Current Nest Presence: ${pres}"
		if(pres == "auto-away" || pres == "away") { setHome() }
		else if (pres == "home") { setAway() }
	}
	catch (ex) {
		log.error "setPresence Exception: ${ex.message}"
	}
}

void setAway() {
	try {
		log.trace "setAway()..."
		parent.setStructureAway(this, "true")
		presenceEvent("away")
	}
	catch (ex) {
		log.error "setAway Exception: ${ex.message}"
	}
}

void setHome() {
	try {
		log.trace "setHome()..."
		parent.setStructureAway(this, "false")
		presenceEvent("home")
	}
	catch (ex) {
		log.error "setHome Exception: ${ex.message}"
	}
}

def setNestEta(tripId, begin, end){
	Logger("setNestEta()...", "trace")
	parent?.setEtaState(this, ["trip_id": "${tripId}", "estimated_arrival_window_begin": "${begin}", "estimated_arrival_window_end": "${end}" ] )
}

def cancelNestEta(tripId){
	Logger("cancelNestEta()...", "trace")
	parent?.cancelEtaState(this, "${tripId}" )
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
		state?.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if(state?.enRemDiagLogging == null) {
			state?.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state?.enRemDiagLogging}"
	}
        if(state?.enRemDiagLogging) {
		String theId = lastN(device.getId().toString(),5)
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

//This will Print logs from the parent app when added to parent method that the child calls
def log(message, level = "trace") {
	Logger("PARENT_Log>> " + message, level)
	return null // always child interface call with a return value
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
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

Long getTimeDiffSeconds(String strtDate, String stpDate=null, String methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if(strtDate) {
		//if(strtDate?.contains("dtNow")) { return 10000 }
		Date now = new Date()
		String stopVal = stpDate ? stpDate : formatDt(now)
		Long startDt = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stopDt = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Long diff = (stop - start) / 1000L //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}
