/**
 *  Nest Camera
 *	Copyright (C) 2018, 2019 Anthony S..
 *	Author: Anthony Santilli (@tonesto7)
 *  Modified: 05/10/2020
 */

import java.text.SimpleDateFormat
import groovy.time.TimeCategory

preferences { }

String devVer() { return "2.0.5" }

metadata {
	definition (name: "Nest Camera", author: "Anthony S.", namespace: "tonesto7", importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/drivers/nstCamera.groovy") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		capability "Motion Sensor"
		capability "Sound Sensor"
		capability "Refresh"
		//capability "Image Capture"
		//capability "Video Camera"
		//capability "Video Capture"

		command "refresh"
		command "poll"
		//command "streamingOn"
		//command "streamingOff"
		command "toggleStreaming"

		attribute "softwareVer", "string"
		attribute "lastConnection", "string"
		attribute "lastOnlineChange", "string"
	//	attribute "lastUpdateDt", "string"
		attribute "isStreaming", "string"
		attribute "audioInputEnabled", "string"
		attribute "videoHistoryEnabled", "string"
		attribute "motionPerson", "string"
		attribute "publicShareEnabled", "string"
		attribute "publicShareUrl", "string"
		attribute "imageUrl", "string"
		attribute "imageUrlHtml", "string"
		attribute "animatedImageUrl", "string"
		attribute "animatedImageUrlHtml", "string"
		attribute "lastEventStart", "string"
		attribute "lastEventEnd", "string"
		attribute "lastEventType", "string"
		attribute "lastEventZones", "string"
		attribute "urlsexpire", "string"
		attribute "apiStatus", "string"
		attribute "onlineStatus", "string"
		attribute "securityState", "string"
	}
	preferences {
		input name: "motionOnPersonOnly", type: "bool", title: "Only Trigger Motion Events When Person is Detected?", defaultValue: false, displayDuringSetup: false
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
		if (logEnable) { runIn(1800,logsOff) }
	} else {
		Logger("initialize(): Ran within last 2 seconds - SKIPPING", "trace", true)
	}
	state.remove("enRemDiagLogging")
}

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	runIn(5, "initialize", [overwrite: true] )
}

void updated() {
	log.trace "Device Updated: (${device?.displayName})..."
	runIn(5, "initialize", [overwrite: true] )
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
	if (txtEnable) { log.debug "parse: ${description}" }
}

void poll() {
	//log.trace "polling parent..."
	parent?.refresh(this)
}

void refresh() {
	poll()
}

void generateEvent(eventData) {
	String dtNow = getDtNow()
	//log.trace("processEvent Parsing data ${eventData}")
	try {
		// Logger("------------START OF API RESULTS DATA------------", "warn")
		if(eventData) {
			def results = eventData?.data
			//log.debug "results: $results"
			state?.restStreaming = true
			state.motionSndChgWaitVal = eventData?.motionSndChgWaitVal ? eventData?.motionSndChgWaitVal.toInteger() : 60
			state.nestTimeZone = eventData?.tz ?: null

			publicShareUrlEvent(results?.public_share_url)
			onlineStatusEvent(results?.is_online?.toString())
			isStreamingEvent(results?.is_streaming)
			securityStateEvent(eventData?.secState)
			publicShareEnabledEvent(results?.is_public_share_enabled?.toString())
			videoHistEnabledEvent(results?.is_video_history_enabled?.toString())
			if(results?.last_is_online_change) { lastOnlineEvent(results?.last_is_online_change?.toString()) }
			apiStatusEvent(eventData?.apiIssues)
			audioInputEnabledEvent(results?.is_audio_input_enabled?.toString())
			softwareVerEvent(results?.software_version?.toString())
			def cur = device?.currentState("isStreaming")?.value.toString()
			if(cur == "on") {
				if(results?.snapshot_url) {
					if(isStateChange(device, "imageUrl", results?.snapshot_url?.toString()) )  { // || isStateChange(device, "imageUrlHtml", results?.snapshot_url?.toString())) {
						sendEvent(name: "imageUrl", value: results?.snapshot_url?.toString(), displayed: false)
						sendEvent(name: 'imageUrlHtml', value: '<img src=' + results?.snapshot_url + '></img>', displayed: false)
					}
				}
				if(results?.last_event) {
					if(results?.last_event?.animated_image_url) {
						if(isStateChange(device, "animatedImageUrl", results?.last_event?.animated_image_url?.toString()) ) { //|| isStateChange(device, "animatedImageUrlHtml", results?.last_event?.animated_image_url?.toString())) {
							sendEvent(name: "animatedImageUrl", value: results?.last_event?.animated_image_url?.toString(), displayed: false)
							sendEvent(name: "animatedImageUrlHtml", value: '<img src=' + results?.last_event?.animated_image_url +'></img>', displayed: false)
							sendEvent(name: "urlsexpire", value: results?.last_event?.urls_expire_time , displayed: false)
						}
					}
					if(results?.last_event.start_time && results?.last_event.end_time) { lastEventDataEvent(results?.last_event, results?.activity_zones) }
				}
			}
			lastUpdatedEvent(false)
			lastCheckinEvent(dtNow)
		}
		return
	}
	catch (ex) {
		log.error "generateEvent Exception: ${ex?.message}"
	}
}

Integer getStateSize()      { return state?.toString().length() }
Integer getStateSizePerc()  { return (Integer)Math.round((stateSize/100000)*100) }
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
	return tz
}

void lastCheckinEvent(String checkin, sendEvt=false) {
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	//Logger("lastCheckin: checkin: ${checkin}", "debug")
	def regex1 = /Z/
	String t0 = checkin.replaceAll(regex1, "-0000")

	String lastConn = t0 ? tf?.format(Date.parse("E MMM dd HH:mm:ss z yyyy", t0)) : "Not Available"
	state.lastConnection = lastConn
	if(sendEvt) {
		def lastChk = device.currentState("lastConnection")?.value
		if(isStateChange(device, "lastConnection", lastConn)) {
			// Logger("Last Nest Check-in was: (${lastConn}) | Previous State: (${lastChk})")
			sendEvent(name: 'lastConnection', value: lastConn?.toString(), displayed: false)
		}
	}
}

void lastOnlineEvent(String dt) {
	String lastOnlVal = device.currentState("lastOnlineChange")?.value
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	//Logger("lastOnlineEvent: dt: ${dt}", "debug")
	def regex1 = /Z/
	String t0 = dt.replaceAll(regex1, "-0000")
	String lastOnl = !t0 ? "Nothing To Show..." : tf?.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0))
	if(isStateChange(device, "lastOnlineChange", lastOnl)) {
		Logger("Last Online Change was: (${lastOnl}) | Previous State: (${lastOnlVal})")
		sendEvent(name: 'lastOnlineChange', value: lastOnl, displayed: true, isStateChange: true)
	}
}

void onlineStatusEvent(String isOnline) {
	//Logger("onlineStatusEvent($isOnline)")
	String prevOnlineStat = device.currentState("onlineStatus")?.value
	String onlineStat = isOnline == "true" ? "online" : "offline"
	if(isStateChange(device, "onlineStatus", onlineStat)) {
		Logger("Online Status is: (${onlineStat}) | Previous State: (${prevOnlineStat})")
		sendEvent(name: "onlineStatus", value: onlineStat.toString(), descriptionText: "Online Status is: ${onlineStat}", displayed: true, isStateChange: true, state: onlineStat)
	}
}

void securityStateEvent(String sec) {
	String val = ""
	String oldState = device.currentState("securityState")?.value
	if(sec) { val = sec }
	if(isStateChange(device, "securityState", val)) {
		Logger("Security State is (${val}) | Previous State: (${oldState})")
		sendEvent(name: "securityState", value: val, descriptionText: "Location Security State is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void isStreamingEvent(isStreaming, override=false) {
	//log.trace "isStreamingEvent($isStreaming)..."
	String isOn = device.currentState("isStreaming")?.value
	String isOnline = device.currentState("onlineStatus")?.value
	String val = (isStreaming.toString() == "true") ? "on" : (isOnline.toString() != "online" ? "offline" : "off")
	if(isStateChange(device, "isStreaming", val)) {
		Logger("Camera Live Video Streaming is: (${val}) | Previous State: (${isOn})")
		sendEvent(name: "isStreaming", value: val, descriptionText: "Camera Video Streaming is: ${val}", displayed: true, isStateChange: true, state: val)
		sendEvent(name: "switch", value: (val == "on" ? val : "off"), displayed: false)
	}
}

void audioInputEnabledEvent(on) {
	String isOn = device.currentState("audioInputEnabled")?.value
	String val = (on.toString() == "true") ? "Enabled" : "Disabled"
	if(isStateChange(device, "audioInputEnabled", val)) {
		Logger("Audio Input Status is: (${val}) | Previous State: (${isOn})")
		sendEvent(name: "audioInputEnabled", value: val, descriptionText: "Audio Input Status is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void videoHistEnabledEvent(on) {
	String isOn = device.currentState("videoHistoryEnabled")?.value
	String val = (on.toString() == "true") ? "Enabled" : "Disabled"
	if(isStateChange(device, "videoHistoryEnabled", val)) {
		Logger("Video History Status is: (${val}) | Previous State: (${isOn})")
		sendEvent(name: "videoHistoryEnabled", value: val, descriptionText: "Video History Status is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void publicShareEnabledEvent(on) {
	String isOn = device.currentState("publicShareEnabled")?.value
	String val = on ? "Enabled" : "Disabled"
	if(isStateChange(device, "publicShareEnabled", val)) {
		Logger("Public Sharing Status is: (${val}) | Previous State: (${isOn})")
		sendEvent(name: "publicShareEnabled", value: val, descriptionText: "Public Sharing Status is: ${val}", displayed: true, isStateChange: true, state: val)
	}
}

void softwareVerEvent(String ver) {
	String verVal = device.currentState("softwareVer")?.value
	if(isStateChange(device, "softwareVer", ver)) {
		Logger("Firmware Version: (${ver}) | Previous State: (${verVal})")
		sendEvent(name: 'softwareVer', value: ver, descriptionText: "Firmware Version is now v${ver}", displayed: false)
	}
}

void lastEventDataEvent(data, actZones) {
	// log.trace "lastEventDataEvent($data)"
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	tf.setTimeZone(getTimeZone())
	//Logger("lastEventDataEvent 1", "debug")
	String curStartDt = device?.currentState("lastEventStart")?.value ? tf?.format(Date.parse("E MMM dd HH:mm:ss z yyyy", device?.currentState("lastEventStart")?.value.toString())) : null
	//Logger("lastEventDataEvent 2 curStartDt: ${curStartDt}", "debug")
	String curEndDt = device?.currentState("lastEventEnd")?.value ? tf?.format(Date.parse("E MMM dd HH:mm:ss z yyyy", device?.currentState("lastEventEnd")?.value.toString())) : null
	//Logger("lastEventDataEvent 2 curEndDt: ${curEndDt}", "debug")

	def regex1 = /Z/
	//Logger("lastEventData 3 data.start: ${data?.start_time}")
	String t0 = data?.start_time ? data?.start_time.replaceAll(regex1, "-0000") : data?.start_time
	String newStartDt = data?.start_time ? tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0)) : "Not Available"
	//Logger("lastEventDataEvent 3 newStartDt: ${newStartDt}", "debug")

	//Logger("lastEventData 3 data.end_time: ${data?.end_time}")
	String t1 = data?.end_time ? data?.end_time.replaceAll(regex1, "-0000") : data?.end_time
	String newEndDt = data?.end_time ? tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t1)) : "Not Available"
	//Logger("lastEventDataEvent 3 newEndDt: ${newEndDt}", "debug")

	Boolean hasPerson = data?.has_person ? data?.has_person?.toBoolean() : false
	Boolean hasMotion = data?.has_motion ? data?.has_motion?.toBoolean() : false
	Boolean hasSound = data?.has_sound ? data?.has_sound?.toBoolean() : false
	def evtZoneIds = data?.activity_zone_ids
	String evtZoneNames = (String)null

	String evtType = (!hasMotion ? "Sound Event" : "Motion Event") + "${hasPerson ? " (Person)" : ""}" + "${hasSound ? " (Sound)" : ""}"
	if(actZones && evtZoneIds) {
		state?.activityZones = actZones?.collect { it?.name }
		evtZoneNames = actZones.findAll { it?.id.toString() in evtZoneIds }.collect { it?.name }
		String zstr = ""
		Integer i = 1
		evtZoneNames?.sort()?.each {
			zstr += "${(i > 1 && i <= evtZoneNames?.size()) ? "<br>" : ""}${it}"
			i = i+1
		}
	}

	state.lastEventDate = formatDt2(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0), "MMMMM d, yyyy")
	state.lastEventTime = "${formatDt2(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t0), "h:mm:ssa")} to ${formatDt2(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", t1), "h:mm:ssa")}"
	if(state?.lastEventData) { state.lastEventData == null }

	Boolean tryPic = false
    
	if(!evtZoneNames) evtZoneNames = "not set"
	if(!state?.lastCamEvtData || (curStartDt != newStartDt || curEndDt != newEndDt) || isStateChange(device, "lastEventType", evtType) || isStateChange(device, "lastEventZones", evtZoneNames)) {
		if(hasPerson || hasMotion || hasSound) {
			//log.debug "curStartDt: $curStartDt | curEndDt: $curEndDt || newStartDt: $newStartDt | newEndDt: $newEndDt"
			//log.debug "lastEventType: $evtType | lastEventZones: ${evtZoneNames.toString()}"
			sendEvent(name: 'lastEventStart', value: newStartDt, descriptionText: "Last Event Start is ${newStartDt}", displayed: false)
			sendEvent(name: 'lastEventEnd', value: newEndDt, descriptionText: "Last Event End is ${newEndDt}", displayed: false)
			sendEvent(name: 'lastEventType', value: evtType, descriptionText: "Last Event Type was ${evtType}", displayed: false)
			sendEvent(name: 'lastEventZones', value: evtZoneNames.toString(), descriptionText: "Last Event Zones: ${evtZoneNames}", displayed: false)
			state.lastCamEvtData = ["startDt":newStartDt, "endDt":newEndDt, "hasMotion":hasMotion, "hasSound":hasSound, "hasPerson":hasPerson, "motionOnPersonOnly":(settings?.motionOnPersonOnly == true), "actZones":(data?.activity_zone_ids ?: null)]
			if(data?.start_time && GetTimeDiffSeconds(newStartDt) < 180L) {
				Logger("└────────────────────────────")
				Logger("│	HasSound: (${hasSound})")
				Logger("│	HasPerson: (${hasPerson})")
				//Logger("│	Took Snapshot: (${tryPic})")
				Logger("│	Zones: ${evtZoneNames ?: "None"}")
				Logger("│	End Time: (${newEndDt})")
				Logger("│	Start Time: (${newStartDt})")
				Logger("│	Type: ${evtType}")
				Logger("┌────────New Camera Event────────")
			} else {
				Logger("Start Time out of time range: (${newStartDt})")
			}
		}
	}
	motionSoundEvtHandler()
}

void motionSoundEvtHandler() {
	def data = state?.lastCamEvtData
	if(data) {
		motionEvtHandler(data)
		data = state?.lastCamEvtData
		soundEvtHandler(data)
	}
}

void motionEvtHandler(data) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	tf.setTimeZone(getTimeZone())
	Date dtNow = new Date()
	String curMotion = device.currentState("motion")?.value?.toString()
	String motionStat = "inactive"
	String motionPerStat = "inactive"
	if(state?.restStreaming == true && data) {
		if(data?.endDt && data?.hasMotion && !data?.sentMUpd) {
			Long t0 = GetTimeDiffSeconds(data?.startDt, data?.endDt)
			Integer t1 = state?.motionSndChgWaitVal ?: 4
			Integer newDur = Math.min( Math.max(3, t0.toInteger()) , t1)

			t0 = GetTimeDiffSeconds(data?.endDt)
			Long howRecent = Math.max(1L, t0)
			//Logger("MOTION NewDur: ${newDur}    howRecent: ${howRecent}")

			def tt0 = state?.lastCamEvtData
			tt0.sentMUpd = true
			state.lastCamEvtData = tt0
			if(howRecent <= 60L) {
				Boolean motGo = (data?.motionOnPersonOnly == true && data?.hasPerson != true) ? false : true
				if(motGo) {
					motionStat = "active"
					if(data?.hasPerson) { motionPerStat = "active" }
					runIn(newDur, "motionSoundEvtHandler", [overwrite: true])
				}
			}
/*
			def newEndDt = null
			use( TimeCategory ) {
				newEndDt = Date.parse("E MMM dd HH:mm:ss z yyyy", data?.endDt.toString())+1.minutes
			}
			if(newEndDt) {
				def motGo = (data?.motionOnPersonOnly == true && data?.hasPerson != true) ? false : true
				if(newEndDt > dtNow && motGo) {
					motionStat = "active"
					if(data?.hasPerson) { motionPerStat = "active" }
					runIn(state?.motionSndChgWaitVal.toInteger()+6, "motionSoundEvtHandler", [overwrite: true])
				}
			}
*/
		}
	}
	if(isStateChange(device, "motion", motionStat) ) {
		Logger("Motion Sensor is: (${motionStat}) | Person: (${motionPerStat}) | Previous State: (${curMotion})")
		sendEvent(name: "motion", value: motionStat, descriptionText: "Motion Sensor is: ${motionStat}", displayed: true, isStateChange: true, state: motionStat)
	}
	if(isStateChange(device, "motionPerson", motionPerStat)) {
		sendEvent(name: "motionPerson", value: motionPerStat, descriptionText: "Motion Person is: ${motionPerStat}", displayed: true, isStateChange: true, state: motionPerStat)
	}
}

void soundEvtHandler(data) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	tf.setTimeZone(getTimeZone())
	Date dtNow = new Date()
	String curSound = device.currentState("sound")?.value?.toString()
	String sndStat = "not detected"
	if(state?.restStreaming == true && data) {
		if(data?.endDt && data?.hasSound && !data?.sentSUpd) {
			Long t0 = GetTimeDiffSeconds(data?.startDt, data?.endDt)
			Integer t1 = state?.motionSndChgWaitVal ?: 4
			Integer newDur = Math.min( Math.max(3, t0.toInteger()) , state?.motionSndChgWaitVal)

			t0 = GetTimeDiffSeconds(data?.endDt)
			Long howRecent = Math.max(1L, t0)
			//Logger("SOUND NewDur: ${newDur}    howRecent: ${howRecent}")

			def tt0 = state?.lastCamEvtData
			tt0.sentSUpd = true
			state.lastCamEvtData = tt0
			if(howRecent <= 60L) {
				sndStat = "detected"
				runIn(newDur, "motionSoundEvtHandler", [overwrite: true])
			}

/*
			def newEndDt = null
			use( TimeCategory ) {
				newEndDt = Date.parse("E MMM dd HH:mm:ss z yyyy", data?.endDt.toString())+1.minutes
			}
			if(newEndDt) {
				if(newEndDt > dtNow) {
					sndStat = "detected"
					runIn(state?.motionSndChgWaitVal.toInteger()+6, "motionSoundEvtHandler", [overwrite: true])
				}
			}
*/
		}
	}
	if(isStateChange(device, "sound", sndStat)) {
		Logger("Sound Detector: (${sndStat}) | Previous State: (${curSound})")
		sendEvent(name: "sound", value: sndStat, descriptionText: "Sound Sensor is: ${sndStat}", displayed: true, isStateChange: true, state: sndStat)
	}
}


void apiStatusEvent(String issueDesc) {
	String curStat = device.currentState("apiStatus")?.value
	String newStat = issueDesc
	if(isStateChange(device, "apiStatus", newStat)) {
		Logger("API Status is: (${newStat.capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
	}
}

void lastUpdatedEvent(Boolean sendEvt=false) {
	Date now = new Date()
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	String lastDt = tf.format(now)
	state.lastUpdatedDt = lastDt
	// state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		String lastUpd = device.currentState("lastUpdatedDt")?.value
		sendEvent(name: 'lastUpdatedDt', value: lastDt, displayed: false, isStateChange: true)
	}
}

void publicShareUrlEvent(String url) {
	//log.trace "publicShareUrlEvent($url)"
	if(isStateChange(device, "publicShareUrl", url)) {
		sendEvent(name: "publicShareUrl", value: url)
	}
}

def getPublicVidID() {
	def id = null
	if(!state?.pubVidId && state?.public_share_url) {
		id = state?.public_share_url.tokenize('/')[3].toString()
		state?.pubVidId = id
	} else {
		id = state?.pubVidId
	}
	return id
}

String getRecTimeDesc(val) {
	String result = null
	if(val && val instanceof Integer) {
		if(val.toInteger() > 24) {
			def nVal = (val/24).toDouble().round(0) //
			result = "${nVal.toInteger()} days"
		} else {
			result = "${val} hours"
		}
	}
	return result
}

/************************************************************************************************
 |									DEVICE COMMANDS     										|
 *************************************************************************************************/
void toggleStreaming() {
	String cur = device?.currentState("isStreaming")?.value.toString()
	if(cur == "on" || cur == "unavailable" || !cur) {
		streamingOff(true)
	} else {
		streamingOn(true)
	}
}

void streamingOn(Boolean manChg=false) {
	try {
		Logger("Sending Camera Stream ON Command...")
		parent?.setCamStreaming(this, "true")
	} catch (ex) {
		log.error "streamingOn Exception: ${ex?.message}"
	}
}

void streamingOff(Boolean manChg=false) {
	try {
		Logger("Sending Camera Stream OFF Command...")
		parent?.setCamStreaming(this, "false")
	} catch (ex) {
		log.error "streamingOff Exception: ${ex?.message}"
	}
}

void on() {
	streamingOn()
}

void off() {
	streamingOff()
}

void flip() {
	Logger("Nest API Doesn't support the FLIP command...", "warn")
}

void mute() {
	Logger("Nest API Doesn't support the MUTE command...", "warn")
}

void unmute() {
	Logger("Nest API Doesn't support the UNMUTE command...", "warn")
}

/*******************************************************************************
 |							LOGGING FUNCTIONS									|
 ********************************************************************************/

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
		parent.saveLogtoRemDiagStore(smsg, logType, "Camera-${theId}")
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

String formatDt(Date dt, Boolean mdy=false) {
	//log.trace "formatDt($dt, $mdy)..."
	String formatVal = mdy ? "MMM d, yyyy - h:mm:ss a" : "E MMM dd HH:mm:ss z yyyy"
	def tf = new SimpleDateFormat(formatVal)
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

String formatDt2(Date dt, String fmt=null) {
	//log.trace "formatDt($dt, $mdy)..."
	def tf = new SimpleDateFormat(fmt)
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}

Long GetTimeDiffSeconds(String strtDate, String stpDate=(String)null, String methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		Date now = new Date()
		String stopVal = stpDate ? stpDate : formatDt(now)
		Long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Long diff = (stop - start) / 1000L //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}

/*
def epochToTime(tm) {
	def tf = new SimpleDateFormat("h:mm a")
	tf?.setTimeZone(getTimeZone())
	return tf.format(tm)
}

def isTimeBetween(start, end, now, tz) {
	def startDt = Date.parse("E MMM dd HH:mm:ss z yyyy", start).getTime()
	def endDt = Date.parse("E MMM dd HH:mm:ss z yyyy", end).getTime()
	def nowDt = Date.parse("E MMM dd HH:mm:ss z yyyy", now).getTime()
	def result = false
	if(nowDt > startDt && nowDt < endDt) {
		result = true
	}
	return result
}
*/
