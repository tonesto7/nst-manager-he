/**
 *  Nest Eventstream
 *	Copyright (C) 2018, 2019 Anthony S..
 *	Author: Anthony Santilli (@tonesto7)
 *  Modified: 04/17/2020
 */

import java.text.SimpleDateFormat
import groovy.json.*
import java.security.MessageDigest
import groovy.transform.Field

static def devVer() { return "2.0.6" }

metadata {
	definition (name: "Nest Eventstream", namespace: "tonesto7", author: "Anthony S.", importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/drivers/nstEventstream.groovy") {
		capability "Initialize" //Runs on hub startup
		command "streamStart"
		command "streamStop"
		attribute "streamStatus", "string"
		attribute "lastConnection", "string"
		attribute "apiStatus", "string"
	}

	preferences {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
	}
}

void logsOff(){
	Logger("${device.displayName} debug logging disabled...")
	device.updateSetting("logEnable",[value:"false",type:"bool"])
	state.remove("enRemDiagLogging")
}

void installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	setStreamStatusVal(false)
	blockStreaming(true)
	parent?.streamDeviceInstalled(true)
}

void initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	if(state.streamRunning) {
		parent.streamDeviceInstalled(true)
		streamStart()
		if (logEnable) runIn(1800, logsOff)
	}
}

void updated() {
	Logger("Device Updated: (${device?.displayName})...")
	Logger("debug logging is: ${logEnable} | description logging is: ${txtEnable}")
	if (logEnable) runIn(1800, logsOff)
	state.remove("enRemDiagLogging")
//cleanups
	state.remove("lastEventData")
	state.remove("camSave")
	state.remove("lastUpdatedDt")
	state.remove("lastUpdatedDtFmt")
	state.remove("allEventCount")
	state.remove("eventCount")
	parent?.streamDeviceInstalled(true)
}

void uninstalled() {
	streamStop()
	parent?.streamDeviceInstalled(false)
	log.trace "Device Removed: (${device?.displayName})..."
}

void poll() {
	parent?.refresh(this)
}

void refresh() {
	poll()
}

void verifyDataAttr() {
	if(!device?.getDataValue("manufacturer")) {
		updateDataValue("manufacturer", "Nest")
	}
	if(!device?.getDataValue("model")) {
		updateDataValue("model", device?.name as String)
	}
}

void stateRemove(key) {
        state.remove(key?.toString())
}

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDevTypeId() { return device?.getDevTypeId() }

def getDeviceStateData() {
	return getState()
}

Boolean isStreamDevice() { return true }

// called by parent
void blockStreaming(Boolean val) {
	if (state.blockEventStreaming != val) {
		Logger("blockStreaming(${val})")
	}
	state.blockEventStreaming = val
}

// called by parent
void streamStart() {
	Logger("streamStart()")
	if(state.streamRunning) {
		Logger("eventStream() already running...", "error")
		streamStop()
		return
	}
	Logger("Starting eventStream()...")
	String tkn = parent?.getNestAuthToken()
	String url = parent?.getNestApiUrl()
	state.structure = parent.getStructure()
	if(tkn && url && state.structure) {
		unschedule("sendRecent")  // or runIn(6000, "sendRecent", [overwrite: true])
		state.runInSlowActive = false
		allEventCountFLD = 0
		eventCountFLD = 0
		state.sentForceNull = false
		state.savedmymeta = null
		state.savedmystruct = null
		state.savedmythermostats = [:]
		state.savedmythermostatsorig = [:]
		state.savedmyprotects = [:]
		state.savedmyprotectsorig = [:]
		state.savedmycamerasorig = [:]
		//state.camSave = [:]
		camSaveFLD = [:]
		//state.lastEventData = [:]
		lastEventDataFLD = [:]
		if(!logEnable) {
			device.updateSetting("logEnable",[value:"true",type:"bool"])
			runIn(900, logsOff)
		}
		lastUpdatedEvent(true)
		eventStreamConnect(url, "Bearer ${tkn}")
	} else {
		Logger("Unable to start stream... Missing Token: ($tkn) or API Url: [${url}] or structure [${state.structure}]", "warn")
		setStreamStatusVal(false)
	}
}

// called by parent
void streamStop() {
	Logger("Stream Stopping...")
	Boolean sendNull = false
	if(state.streamRunning) {
		sendNull = true
	}
	setStreamStatusVal(false)
	blockStreaming(true)
	if(sendNull) { sendRecent(true) }
	eventStreamClose()
}

@Field static Long allEventCountFLD 
@Field static Long eventCountFLD 
@Field static Map lastEventDataFLD
@Field static Map camSaveFLD

void parse(description) {
	//log.warn "Event: ${description}"
//	try {
		if (!state.blockEventStreaming && description) {
			def data = new JsonSlurper().parseText(description as String)
			if (data?.size()) {
				allEventCountFLD = allEventCountFLD ? allEventCountFLD + 1 : 1L
				//Logger("Stream Event Received...", "info")
				Boolean chgd = false
				Boolean somechg = false
				if(state.structure) {
					//def theRawEvent = new JsonSlurper().parseText(description as String)
					//def theRawEvent = new JsonSlurper().parseText(JsonOutput.toJson(data))
					def theRawEvent = [:] + data // make a copy

					// This is the "cleaned up" event we will send to NST manager.
					def theNewEvent = [:]
					theNewEvent.path = theRawEvent?.path
					theNewEvent.data = [:]
					theNewEvent.data.metadata = [:]
					theNewEvent.data.devices = [:]
					theNewEvent.data.structures = [:]

					def mydata = [:]
					mydata = data?.data as Map
					if(!mydata) { Logger("No Data in mydata", "warn") }

					def tempmymeta = [:]
					tempmymeta = mydata?.metadata

					Boolean chgFound = true
					def tchksum

					if(tempmymeta) {
						theNewEvent.data.metadata = [:] + theRawEvent.data.metadata
						tchksum = generateMD5_A(tempmymeta.toString())
						if(tchksum == state.savedmymeta) { chgFound = false }
						//chgFound = getChanges(tempmymeta, state.savedmymeta, "/metatdata", "metadata")
					}
					if(tempmymeta && ( !state.savedmymeta || chgFound )) {
						chgd = true
						state.savedmymeta = tchksum
						//Logger("tempmymeta changed", "info")
						//Logger("chgFound ${chgFound}", "info")
						//Logger("tempmymeta ${tempmymeta.toString()}", "info")
						//Logger("state.savedmyymeta ${state.savedmymeta.toString()}", "info")
					}

					def mystruct = [:]
					if(!mydata?.structures) { Logger("No Data in structures", "warn"); return }
					mystruct = mydata?.structures?."${state.structure}"
					if(!mystruct) { /* Logger("No Data in structure ${state.structure}", "warn");*/ return }
					theNewEvent.data.structures."${state.structure}" = [:] + mystruct

					tchksum = null
					chgFound = true
					def st0 = [:] + mystruct
					//def st1 = state.savedmystruct
					if(st0) {
						st0.wheres = [:]
						tchksum = generateMD5_A(st0.toString())
						if(tchksum == state.savedmystruct) { chgFound = false }
						//chgFound = getChanges(st0, st1, "/structure", "structure")
					} else {
						Logger("No Data in structures ${state.structure}", "warn")
						return
					}

					if(st0 && ( !state.savedmystruct || chgFound)) {
						chgd = true
						state.savedmystruct = tchksum
						//Logger("mystruct changed structure ${state.structure}", "info")
						//Logger("mystruct chgFound ${chgFound}", "info")
					}

					if(mystruct?.thermostats) {
						theNewEvent.data.devices.thermostats = [:]
						def tlen = mystruct.thermostats.size()
						for (i = 0; i < tlen; i++) {
							def t1 = mystruct.thermostats[i]

							def adjT1 = [:]
							adjT1 = mydata.devices.thermostats[t1]
							if(!adjT1) { Logger("No Data in thermostat ${i} ${t1}", "warn"); return }
							theNewEvent.data.devices.thermostats."${t1}" = [:] + adjT1
	
							def t0 = [:]
							def prevCheckSum = //[:]
							t0 = state.savedmythermostatsorig ?: [:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }
							//Logger("thermostat ${i} ${t1} adjT1 ${adjT1}", "debug")
							//Logger("thermostat ${i} ${t1} prevCheckSum ${prevCheckSum}", "debug")

							tchksum = null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(adjT1, adjT2, "/thermostats", "tstat")
							}
							if(adjT1 && ( !prevCheckSum || chgFound )) {
								//t0 = state.savedmythermostatsorig
								t0[t1] = tchksum
								state.savedmythermostatsorig = t0
								somechg = true
							}

							prevCheckSum = //[:]
							t0 = state.savedmythermostats ?: [:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							tchksum = null
							chgFound = true
							if(adjT1) {
								//def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								def at0 = [:] + adjT1 // make a copy
								at0.last_connection = ""
								//at0.where_id = ""

								//def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								//def at1 = [:] + adjT2
								//at1.last_connection = ""
								//at1.where_id = ""
								tchksum = generateMD5_A(at0.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(at0, at1, "/thermostats", "tstat")
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0[t1] = tchksum
								state.savedmythermostats = t0
								chgd = true
								//Logger("thermostat changed ${t1}", "info")
								//Logger("tstat chgFound ${chgFound}", "info")
							}
						}
					} // else Logger("no thermostats", "warn")
					if(mystruct?.smoke_co_alarms) {
						theNewEvent.data.devices.smoke_co_alarms = [:]
						def tlen = mystruct.smoke_co_alarms.size()
						for (i = 0; i < tlen; i++) {
							def t1 = mystruct.smoke_co_alarms[i]
							def adjT1 = [:]
							adjT1 = mydata.devices.smoke_co_alarms[t1]
							if(!adjT1) { Logger("No Data in smoke_co_alarms ${i} ${t1}", "warn"); return }
							theNewEvent.data.devices.smoke_co_alarms."${t1}" = [:] + adjT1

							def prevCheckSum // = [:]
							def t0 = state.savedmyprotectsorig ?: [:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							tchksum = null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(adjT1, adjT2, "/protects", "prot")
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0[t1] = tchksum
								state.savedmyprotectsorig = t0
								somechg = true
							}

							prevCheckSum = null // this is a checksum [:]
							t0 = state.savedmyprotects ?: [:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							tchksum = null
							chgFound = true
							if(adjT1) {
								//def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								def at0 = [:] + adjT1 // make a copy
								at0.last_connection = ""
								//at0.where_id = ""
								//def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								//def at1 = [:] + adjT2
								//at1.last_connection = ""
								//at1.where_id = ""
								tchksum = generateMD5_A(at0.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(at0, at1, "/protects", "prot")
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0[t1] = tchksum
								state.savedmyprotects = t0
								chgd = true
								//Logger("protect changed ${t1}", "info")
								//Logger("prot chgFound ${chgFound}", "info")
							}
						}
					} // else Logger("no protects", "warn")
					if(mystruct?.cameras) {
						theNewEvent.data.devices.cameras = [:]
						def camSave = [:]
						def tlen = mystruct.cameras.size()
						for (i = 0; i < tlen; i++) {
							def t1 = mystruct.cameras[i]

							def adjT1 = [:]
							adjT1 = mydata.devices.cameras[t1]
							if(!adjT1) { Logger("No Data in cameras ${i} ${t1}", "warn"); return }

							def t0 = state.savedmycamerasorig ?: [:]
							def prevCheckSum // = [:]   this is a checksum [:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							def myisonline = adjT1?.is_online
							def myisstreaming = adjT1?.is_streaming
							if(!myisonline || !myisstreaming) {
								adjT1.web_url = ""
								adjT1.snapshot_url = ""
								adjT1.app_url = ""
								adjT1.last_event.image_url = ""
								adjT1.last_event.web_url = ""
								adjT1.last_event.app_url = ""
								adjT1.last_event.animated_image_url = ""
							}
							theNewEvent.data.devices.cameras."${t1}" = [:] + adjT1

							tchksum = null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(adjT1, adjT2, "/cameras", "cam")
							}
							if(adjT1 && (!prevCheckSum || chgFound)) {     //adjT1.toString() != adjT2.toString()) {
								t0[t1] = tchksum
								state.savedmycamerasorig = t0
								if(!myisonline || !myisstreaming) {
									somechg = true
									chgFound = false
								}
							}

/*
							t0 = state.savedmycameras ?: [:]
							prevCheckSum = //[:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							tchksum = null
							chgFound = true
*/
							//if(adjT1) {
/*
								def at0 = [:] + adjT1
								def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								at0.where_id = ""

								def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								at1.where_id = ""
*/
								//def at1 = adjT2
								//tchksum = generateMD5_A(at0.toString())
								//if(tchksum == prevCheckSum) { chgFound = false }
								//chgFound = getChanges(at0, at1, "/cameras", "cam")
							//}
							if(adjT1 && ( !prevCheckSum || chgFound)) {    //adjT1.toString() != adjT2.toString()) {
								//t0[t1] = tchksum
								//state.savedmycameras = t0
								chgd = true
								// Logger("camera changed ${t1}", "info")
								// Logger("camera chgFound ${chgFound}", "info")
							}

/*
							//t0 = theRawEvent as Map
							t0 = theNewEvent as Map
							def tempNewData_data = t0?.data as Map
							def tempCurrStructure = tempNewData_data?.structures?."${state.structure}"
//							Logger("camera tempCurrStructure ${tempCurrStructure}", "info")
							t1 = tempCurrStructure.cameras[i]
							if(!myisonline || !myisstreaming) {
								t0.data.devices.cameras[t1].web_url = ""
								t0.data.devices.cameras[t1].snapshot_url = ""
								t0.data.devices.cameras[t1].app_url = ""
								t0.data.devices.cameras[t1].last_event.image_url = ""
								t0.data.devices.cameras[t1].last_event.web_url = ""
								t0.data.devices.cameras[t1].last_event.app_url = ""
								t0.data.devices.cameras[t1].last_event.animated_image_url = ""
								theNewEvent = t0
							}
*/
							//theNewEvent.data.devices.cameras."${t1}" = [:] + adjT1
							camSave[t1] = [:] + adjT1
						}
						if(camSave) {
							//state.camSave = camSave
							camSaveFLD = camSave
							//Logger("updating camSave", "warn")
						}
					}  else {
						//if(state.camSave) {
						if(camSaveFLD) {
							//Logger("using camSave", "warn")
							theNewEvent.data.devices.cameras = [:]
							//theNewEvent.data.devices.cameras = state.camSave
							theNewEvent.data.devices.cameras = camSaveFLD
						} //else Logger("no cameras", "warn")
					}

					//state.lastEventData = theNewEvent
					lastEventDataFLD = theNewEvent
				}
				else {
					Logger("no state.structure", "error")
					chgd = true
				}
				setStreamStatusVal(true)
				if(!chgd && somechg) {
					if(!state.runInSlowActive) {
						state.runInSlowActive = true
						Logger("scheduling event", "info")
						runIn(95, "sendRecent", [overwrite: true])
					}
				}
				if(chgd) {
					if(state.runInSlowActive) {
						Logger("unscheduling event", "info")
						unschedule("sendRecent")  // or runIn(6000, "sendRecent", [overwrite: true])
						state.runInSlowActive = false
					}
					runIn(2, "sendRecent", [overwrite: true])
					//sendRecent()
				}
			}
		} else {
			state.savedmymeta = null
			state.savedmystruct = null
			state.savedmythermostats = [:]
			state.savedmythermostatsorig = [:]
			state.savedmyprotects = [:]
			state.savedmyprotectsorig = [:]
			state.savedmycamerasorig = [:]
			//state.camSave = [:]
			camSaveFLD = [:]
		}
/*
	} catch (ex) {
		log.error "parse Error: ${ex.message}" // no need to restart stream here if error
	}
*/
	return
}

String generateMD5_A(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

def getChanges(mapA, mapB, String headstr, String objType=null) {
	def t0 = mapA
	def t1 = mapB
	def left = t0
	def right = t1
	def itemsChgd = []
	//Logger("getChanges ${headstr} t0 ${t0}", "info")
	//Logger("getChanges ${headstr} t1 ${t1}", "info")
	if (left instanceof Map) {
		String[] leftKeys = left.keySet()
		String[] rightKeys = right.keySet()
		leftKeys.each {
			if( left[it] instanceof Map ) {
				def chgs = getChanges( left[it], right[it], "${headstr}/${it}", objType )
				if(chgs && objType) {
					itemsChgd += chgs
				}
			} else {
				if (left[it].toString() != right[it].toString()) {
       					 if(objType) {
//						Logger("getChanges ${headstr} IT: ${it}  LEFT: ${left[it]}   RIGHT:${right[it]}")
						itemsChgd.push(it.toString())
       				 	}
				}
			}
		}
		if(itemsChgd.size()) {
			//Logger("returning items ${itemsChgd}", "info")
			return itemsChgd
		}
	}
	return false
}

void sendRecent(Boolean forceNull=false) {
	def t0 = [:]
	//t0 = state.lastEventData
	t0 = lastEventDataFLD
	state.runInSlowActive = false
	if(t0 || forceNull) {
		if(forceNull && !state.sentForceNull) {
			t0 = [:]
			state.sentForceNull = true
			Logger("Forced Null sent Event # ${eventCountFLD} / ${allEventCountFLD}")
		} else {
			if(forceNull) {
				Logger("Skipping Forced Null sent Event # ${eventCountFLD} / ${allEventCountFLD}", "warn")
				return
			}
			eventCountFLD = eventCountFLD ? eventCountFLD + 1 : 1L
			Logger("Sent Event Data Event # ${eventCountFLD} / ${allEventCountFLD}", "info")
		}
		parent?.receiveEventData(t0)
	} else {
		Logger("No Event Data to send  Event # ${eventCountFLD} / ${allEventCountFLD}", "warn")
	}
}

void setStreamStatusVal(Boolean active) {
	state.streamRunning = active
	String curStat = device.currentState("streamStatus")?.value
	String newStat = active ? "running" : "stopped"
	if(isStateChange(device, "streamStatus", newStat.toString())) {
		Logger("Stream Status is: (${newStat.capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "streamStatus", value: newStat, descriptionText: "Rest Stream is: ${(active ? "Running" : "Stopped")}", displayed: true)
	}
}

def eventStreamStatus(String msg) {
	if(!msg.contains("ALIVE:")) { Logger("Status: ${msg}") }
	if (msg.contains("STOP:")) {
		streamStop()
		//setStreamStatusVal(false)
	} else if (msg.contains("ALIVE:")) {
		setStreamStatusVal(true)
	} else if (msg.contains("ERROR:")) {
		streamStop()
		Logger(msg, "error")
	}
	apiStatusEvent(msg)
}

def getTimeZone() {
	def tz = null
	if (location?.timeZone) { tz = location?.timeZone }
	else { tz = state.nestTimeZone ? TimeZone.getTimeZone(state.nestTimeZone) : null }
	if (!tz) { Logger("getTimeZone: Hub or Nest TimeZone is not found...", "warn") }
	return tz
}

void apiStatusEvent(String issueDesc) {
	String curStat = device.currentState("apiStatus")?.value
	String newStat = issueDesc
	state.apiStatus = newStat
	if(isStateChange(device, "apiStatus", newStat.toString())) {
		Logger("API Status is: (${newStat.toString().capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true)
	}
}

void lastUpdatedEvent(Boolean sendEvt=false) {
	Date now = new Date()
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	String lastUpd = device.currentState("lastConnection")?.value
	String lastDt = tf.format(now)
//	state.lastUpdatedDt = lastDt?.toString()
//	state.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		// Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastConnection', value: lastDt, displayed: false, isStateChange: true)
	}
}

/************************************************************************************************
 |										LOGGING FUNCTIONS										|
 *************************************************************************************************/

static String lastN(String input, Integer n) {
	return n > input?.size() ? input : input[-n..-1]
}

void Logger(String msg, String logType = "debug") {
	if(!logEnable || !msg) { return }
	String smsg = "${device.displayName} (v${devVer()}) | ${msg}"
	if(state.enRemDiagLogging == null) {
		state.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if(state.enRemDiagLogging == null) {
			state.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state.enRemDiagLogging}"
	}
        if(state.enRemDiagLogging) {
		String theId = lastN(device.getId().toString(),5)
                parent.saveLogtoRemDiagStore(smsg, logType, "EventStream-${theId}")
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
	return null // always call child interface with a return value
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

def getSettingVal(String var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

String formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}
