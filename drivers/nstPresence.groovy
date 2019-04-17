/**
 *  Nest Presence
 *	Copyright (C) 2018, 2019 Anthony Santilli.
 *	Author: Anthony Santilli (@tonesto7), Eric Schott (@imnotbob)
 *  Modified: 04/16/2019
 */

import java.text.SimpleDateFormat

def devVer() { return "2.0.1" }

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

def logsOff(){
	log.warn "${device?.displayName} debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	runIn(5, "initialize", [overwrite: true])
}

def initialize() {
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

def verifyDataAttr() {
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

def generateEvent(eventData) {
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
		return null
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

def lastUpdatedEvent(sendEvt=false) {
	//def now = new Date()
	//def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	//tf.setTimeZone(getTimeZone())
	def lastUpd = device.currentState("lastUpdatedDt")?.value
	def lastDt = getDtNow() //"${tf?.format(now)}"
	state?.lastUpdatedDt = lastDt?.toString()
	//state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastUpdatedDt', value: lastDt?.toString(), displayed: false, isStateChange: true)
	}
}

def lastConnectionEvent(checkin, sendEvt=false) {
	def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	def regex1 = /Z/
	checkin = checkin.replaceAll(regex1, "-0000")
	tf.setTimeZone(getTimeZone())

	//def curConn = checkin ? "${tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", checkin))}" : "Not Available"
	def curConnFmt = checkin ? "${formatDt(Date.parse("E MMM dd HH:mm:ss z yyyy", checkin))}" : "Not Available"
	state?.lastConnection = curConnFmt?.toString()

	if(sendEvt) {
		//def curConnSeconds = (checkin && curConnFmt != "Not Available") ? getTimeDiffSeconds(curConnFmt) : 3000

		def lastChk = device.currentState("lastConnection")?.value
		//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000

		 if(isStateChange(device, "lastConnection", curConnFmt.toString())) {
			Logger("Last Nest Check-in was: (${curConnFmt}) | Previous Check-in: (${lastChk})")
			sendEvent(name: 'lastConnection', value: curConnFmt?.toString(), isStateChange: true)
		}
	}
}

def presenceEvent(presence) {
	def val = device.currentState("presence")?.value
	def pres = (presence == "home") ? "present" : "not present"
	def nestPres = !device.currentState("nestPresence") ? null : device.currentState("nestPresence")?.value.toString()
	def newNestPres = (presence == "home") ? "home" : ((presence == "auto-away") ? "auto-away" : "away")
	// def statePres = state?.present
	// state?.present = (pres == "present") ? true : false
	// state?.nestPresence = newNestPres
	if(isStateChange(device, "presence", pres.toString()) || isStateChange(device, "nestPresence", newNestPres.toString()) || !nestPres) {
		Logger("Presence is: ${pres} | Previous State: ${val}")
		sendEvent(name: 'nestPresence', value: newNestPres, descriptionText: "Nest Presence is: ${newNestPres}", displayed: true, isStateChange: true )
		sendEvent(name: 'presence', value: pres, descriptionText: "Device is: ${pres}", displayed: true, isStateChange: true )
	}
}

def etaEvent(String eta) {
	def oeta = device.currentState("etaBegin")?.value
	if(isStateChange(device, "etaBegin", eta.toString())) {
		Logger("Eta Begin is (${eta}) | Previous State: (${oeta})")
		sendEvent(name:'etaBegin', value: eta, descriptionText: "Eta is ${eta}", displayed: true, isStateChange: true)
	}
}

def securityStateEvent(sec) {
	def val = ""
	def oldState = device.currentState("securityState")?.value
	if(sec) { val = sec }
	if(isStateChange(device, "securityState", val.toString())) {
		Logger("Security State is (${val}) | Previous State: (${oldState})")
		sendEvent(name: "securityState", value: val, descriptionText: "Location Security State is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

def peakEvent(start, end) {
	if(start && end) {
		def tf = new java.text.SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
		tf.setTimeZone(getTimeZone())

		def regex1 = /Z/
		def tstart = start.replaceAll(regex1, "-0000")
		def tend = end.replaceAll(regex1, "-0000")
		def startFmt = tstart ? "${formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", tstart))}" : "Not Available"
		def endFmt = tend ? "${formatDt(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", tend))}" : "Not Available"

			def lastChk = device.currentState("peakStart")?.value

			 if(isStateChange(device, "peakStart", startFmt.toString())) {
				Logger("Peak Start: (${startFmt}) | Previous: (${lastChk})")
				sendEvent(name: 'peakStart', value: startFmt?.toString(), isStateChange: true)
			}

			lastChk = device.currentState("peakEnd")?.value
			//def lastConnSeconds = (lastChk && lastChk != "Not Available") ? getTimeDiffSeconds(lastChk) : 3000

			 if(isStateChange(device, "peakEnd", endFmt.toString())) {
				Logger("Peak End: (${endFmt}) | Previous: (${lastChk})")
				sendEvent(name: 'peakEnd', value: endFmt?.toString(), isStateChange: true)
			}
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

//This will Print logs from the parent app when added to parent method that the child calls
def log(message, level = "trace") {
	Logger("PARENT_Log>> " + message, level)
	return null // always child interface call with a return value
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}

def getSettingVal(var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

def formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
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
