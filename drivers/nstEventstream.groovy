/**
 *  Nest Eventstream
 *	Copyright (C) 2018, 2019 Anthony S..
 *	Author: Anthony Santilli (@tonesto7)
 *  Modified: 02/11/2019
 *  -Adjusted the event stream processing.
 *  						-Corey
 */

import java.text.SimpleDateFormat
import groovy.json.*

def devVer() { return "2.0.0" }

metadata {
	definition (name: "Nest Eventstream", namespace: "tonesto7", author: "Anthony S.") {
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

def logsOff(){
	Logger("${device.displayName} debug logging disabled...")
	device.updateSetting("logEnable",[value:"false",type:"bool"])
	state.remove("enRemDiagLogging")
}

def installed() {
	log.trace "Device Installed: (${device?.displayName})..."
	verifyDataAttr()
	setStreamStatusVal(false)
	blockStreaming(true)
	parent?.streamDeviceInstalled(true)
}

def initialize() {
	log.trace "Device Initialized: (${device?.displayName})..."
	if(state?.streamRunning) {
		parent?.streamDeviceInstalled(true)
		streamStart()
	}
}

def updated() {
	Logger("Device Updated: (${device?.displayName})...")
	Logger("debug logging is: ${logEnable} | description logging is: ${txtEnable}")
	if (logEnable) runIn(1800, logsOff)
	state.remove("enRemDiagLogging")
	parent?.streamDeviceInstalled(true)
}

def uninstalled() {
	streamStop()
	parent?.streamDeviceInstalled(false)
	log.trace "Device Removed: (${device?.displayName})..."
}

def poll() {
	parent?.refresh(this)
}

def refresh() {
	poll()
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

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDevTypeId() { return device?.getDevTypeId() }

def getDeviceStateData() {
	return getState()
}

def isStreamDevice() { return true }

// called by parent
def blockStreaming(Boolean val) {
	if (state?.blockEventStreaming != val) {
		Logger("blockStreaming(${val})")
	}
	state.blockEventStreaming = val
}

// called by parent
def streamStart() {
	Logger("streamStart()")
	if(state?.streamRunning) {
		Logger("eventStream() already running...", "error")
		streamStop()
		return
	}
	Logger("Starting eventStream()...")
	def tkn = parent?.getNestAuthToken()
	def url = parent?.getNestApiUrl()
	state.structure = parent?.getStructure()
	if(tkn && url && state?.structure) {
		unschedule("sendRecent")  // or runIn(6000, "sendRecent", [overwrite: true])
		state.runInSlowActive = false
		state.allEventCount = 0
		state.eventCount = 0
		state.sentForceNull = false
		state.savedmymeta = [:]
		state.savedmystruct = [:]
		state.savedmythermostatsorig = [:]
		state.savedmyprotectsorig = [:]
		state.savedmycamerasorig = [:]
		state.lastEventData = [:]
		if(!logEnable) {
			device.updateSetting("logEnable",[value:"true",type:"bool"])
			runIn(900, logsOff)
		}
		lastUpdatedEvent(true)
		eventStreamConnect(url, "Bearer ${tkn}")
	} else {
		Logger("Unable to start stream... Missing Token: ($tkn) or API Url: [${url}] or structure [${state?.structure}]", "warn")
		setStreamStatusVal(false)
	}
}

// called by parent
def streamStop() {
	Logger("Stream Stopping...")
	def sendNull = false
	if(state?.streamRunning) {
		sendNull = true
	}
	setStreamStatusVal(false)
	blockStreaming(true)
	if(sendNull) { sendRecent(true) }
	eventStreamClose()
}

def parse(description) {
	//log.warn "Event: ${description}"
//	try {
		if (!state?.blockEventStreaming && description) {
			def data = new JsonSlurper().parseText(description as String)
			if (data?.size()) {
				state.allEventCount = state.allEventCount + 1
				//Logger("Stream Event Received...", "info")
				def chgd = false
				def somechg = false
				if(state?.structure) {
					//def mylastEventData = new JsonSlurper().parseText(description as String)
					def mylastEventData = new JsonSlurper().parseText(JsonOutput.toJson(data))

					def mydata = [:]
					mydata = data?.data as Map

					if(!mydata) { Logger("No Data in mydata", "warn"); }
					def mymeta = [:]
					mymeta = mydata?.metadata

					def chgFound = true
					if(mymeta && state?.savedmymeta) {
						chgFound = getChanges(mymeta, state?.savedmymeta, "/metatdata", "metadata")
					}
					if(mymeta && ( !state?.savedmymeta || chgFound )) {
						chgd = true
						state.savedmymeta = mymeta
						//Logger("mymeta changed", "info")
						//Logger("chgFound ${chgFound}", "info")
						//Logger("mymeta ${mymeta.toString()}", "info")
						//Logger("state.savedmyymeta ${state?.savedmymeta.toString()}", "info")
					}

					def mystruct = [:]
					if(!mydata?.structures) { Logger("No Data in structures", "warn"); return }
					mystruct = mydata?.structures?."${state.structure}"

					chgFound = true
					def st0
					st0 = mystruct
					def st1 = state?.savedmystruct
					if(mystruct && state?.savedmystruct) {
						st0.wheres = [:]
						st1.wheres = [:]
						state.savedmystruct = st0
						chgFound = getChanges(st0, st1, "/structure", "structure")
					}
					if(mystruct && ( !state?.savedmystruct || chgFound)) {
						chgd = true
						//Logger("mystruct changed structure ${state.structure}", "info")
						//Logger("mystruct chgFound ${chgFound}", "info")
					}

					if(mystruct?.thermostats) {
						def tlen = mystruct.thermostats.size()
						for (i = 0; i < tlen; i++) {
							def t0 = [:]
							def t1 = mystruct.thermostats[i]

							if(!t1) { Logger("No Data in thermostat ${i}", "warn"); return }
							def adjT1 = [:]
							adjT1 = mydata.devices.thermostats[t1]
							def adjT2 = [:]
							t0 = state?.savedmythermostatsorig
							if(t0?."${t1}") { adjT2 = t0[t1] }
							//Logger("thermostat ${i} ${t1} adjT1 ${adjT1}", "debug")
							//Logger("thermostat ${i} ${t1} adjT2 ${adjT2}", "debug")

							chgFound = true
							if(adjT1 && adjT2) {
								chgFound = getChanges(adjT1, adjT2, "/thermostats", "tstat")
							}
							if(adjT1 && ( !adjT2 || chgFound )) {
								t0 = state.savedmythermostatsorig
								t0[t1] = adjT1
								state.savedmythermostatsorig = t0
								somechg = true
							}
							chgFound = true
							if(adjT1 && adjT2) {
								def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								at0.last_connection = ""
								//at0.where_id = ""

								def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								at1.last_connection = ""
								//at1.where_id = ""
								chgFound = getChanges(at0, at1, "/thermostats", "tstat")
							}
							if(adjT1 && ( !adjT2 || chgFound)) {
								chgd = true
								//Logger("thermostat changed ${t1}", "info")
								//Logger("tstat chgFound ${chgFound}", "info")
							}
						}
					}
					if(mystruct?.smoke_co_alarms) {
						def tlen = mystruct.smoke_co_alarms.size()
						for (i = 0; i < tlen; i++) {
							def t0 = [:]
							def t1 = mystruct.smoke_co_alarms[i]

							if(!t1) { Logger("No Data in smoke_co_alarms ${i}", "warn"); return }
							def adjT1 = [:]
							adjT1 = mydata.devices.smoke_co_alarms[t1]
							def adjT2 = [:]
							t0 = state?.savedmyprotectsorig
							if(t0?."${t1}") { adjT2 = t0[t1] }

							chgFound = true
							if(adjT1 && adjT2) {
								chgFound = getChanges(adjT1, adjT2, "/protects", "prot")
							}
							if(adjT1 && ( !adjT2 || chgFound)) {
								t0 = state.savedmyprotectsorig
								t0[t1] = adjT1
								state.savedmyprotectsorig = t0
								somechg = true
							}
							chgFound = true
							if(adjT1 && adjT2) {
								def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								at0.last_connection = ""
								//at0.where_id = ""
								def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								at1.last_connection = ""
								//at1.where_id = ""
								chgFound = getChanges(at0, at1, "/protects", "prot")
							}
							if(adjT1 && ( !adjT2 || chgFound)) {
								chgd = true
								//Logger("protect changed ${t1}", "info")
								//Logger("prot chgFound ${chgFound}", "info")
							}
						}
					}
					if(mystruct?.cameras) {
						def tlen = mystruct.cameras.size()
						for (i = 0; i < tlen; i++) {
							def t0 = [:]
							def t1 = mystruct.cameras[i]

							if(!t1) { Logger("No Data in cameras ${i}", "warn"); return }
							def adjT1 = [:]
							adjT1 = mydata.devices.cameras[t1]
							def adjT2 = [:]
							t0 = state?.savedmycamerasorig
							if(t0?."${t1}") { adjT2 = t0[t1] }

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

							chgFound = true
							if(adjT1 && adjT2) {
								chgFound = getChanges(adjT1, adjT2, "/cameras", "cam")
							}
							if(adjT1 && (!adjT2 || chgFound)) {     //adjT1.toString() != adjT2.toString()) {
								t0 = state.savedmycamerasorig
								t0[t1] = adjT1
								state.savedmycamerasorig = t0
								if(!myisonline || !myisstreaming) {
									somechg = true
								}
							}

							chgFound = true
							if(adjT1 && adjT2) {
								def at0 = adjT1
/*
								def at0 = new JsonSlurper().parseText(JsonOutput.toJson(adjT1))
								at0.where_id = ""

								def at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								at1.where_id = ""
*/
								def at1 = adjT2
								chgFound = getChanges(at0, at1, "/cameras", "cam")
							}
							if(adjT1 && ( !adjT2 || chgFound)) {    //adjT1.toString() != adjT2.toString()) {
								chgd = true
								// Logger("camera changed ${t1}", "info")
								// Logger("camera chgFound ${chgFound}", "info")
							}

							t0 = mylastEventData as Map
							def mydata1 = t0?.data as Map
							def mystruct1 = mydata1?.structures?."${state.structure}"
//							Logger("camera mystruct1 ${mystruct}", "info")
							t1 = mystruct1.cameras[i]
							if(!myisonline || !myisstreaming) {
								t0.data.devices.cameras[t1].web_url = ""
								t0.data.devices.cameras[t1].snapshot_url = ""
								t0.data.devices.cameras[t1].app_url = ""
								t0.data.devices.cameras[t1].last_event.image_url = ""
								t0.data.devices.cameras[t1].last_event.web_url = ""
								t0.data.devices.cameras[t1].last_event.app_url = ""
								t0.data.devices.cameras[t1].last_event.animated_image_url = ""
								mylastEventData = t0
							}
						}
					}
					state.lastEventData = mylastEventData
				}
				else {
					Logger("no state.structure", "error")
					chgd = true
				}
				setStreamStatusVal(true)
				if(!chgd && somechg) {
					if(!state?.runInSlowActive) {
						state.runInSlowActive = true
						Logger("scheduling event", "info")
						runIn(60, "sendRecent", [overwrite: true])
					}
				}
				if(chgd) {
					if(state?.runInSlowActive) {
						Logger("unscheduling event", "info")
						unschedule("sendRecent")  // or runIn(6000, "sendRecent", [overwrite: true])
						state.runInSlowActive = false
					}
					runIn(2, "sendRecent", [overwrite: true])
					//sendRecent()
				}
			}
		} else {
			state.savedmymeta = [:]
			state.savedmystruct = [:]
			state.savedmythermostatsorig = [:]
			state.savedmyprotectsorig = [:]
			state.savedmycamerasorig = [:]
		}
/*
	} catch (ex) {
		log.error "parse Error: ${ex.message}" // no need to restart stream here if error
	}
*/
	return
}

def getChanges(mapA, mapB, headstr, objType=null) {
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

def sendRecent(forceNull=false) {
	def t0 = [:]
	t0 = state?.lastEventData
	state.runInSlowActive = false
	if(t0 || forceNull) {
		if(forceNull && !state?.sentForceNull) {
			t0 = [:]
			state.sentForceNull = true
			Logger("Forced Null sent Event # ${state.eventCount} / ${state.allEventCount}")
		} else {
			if(forceNull) {
				Logger("Skipping Forced Null sent Event # ${state.eventCount} / ${state.allEventCount}", "warn")
				return
			}
			state.eventCount = state.eventCount + 1
			Logger("Sent Event Data Event # ${state.eventCount} / ${state.allEventCount}", "info")
		}
		parent?.receiveEventData(t0)
	} else {
		Logger("No Event Data to send  Event # ${state.eventCount} / ${state.allEventCount}", "warn")
	}
}

def setStreamStatusVal(Boolean active) {
	state?.streamRunning = active
	def curStat = device.currentState("streamStatus")?.value
	def newStat = active ? "running" : "stopped"
	if(isStateChange(device, "streamStatus", newStat.toString())) {
		Logger("Stream Status is: (${newStat.toString().capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "streamStatus", value: newStat, descriptionText: "Rest Stream is: ${(active ? "Running" : "Stopped")}", displayed: true)
	}
}

def eventStreamStatus(String msg) {
	if(!msg.contains("ALIVE:")) { Logger("Status: ${msg}") }
	if (msg.contains("STOP:")) {
		setStreamStatusVal(false)
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
	else { tz = state?.nestTimeZone ? TimeZone.getTimeZone(state?.nestTimeZone) : null }
	if (!tz) { Logger("getTimeZone: Hub or Nest TimeZone is not found...", "warn") }
	return tz
}

def apiStatusEvent(issueDesc) {
	def curStat = device.currentState("apiStatus")?.value
	def newStat = issueDesc
	state?.apiStatus = newStat
	if(isStateChange(device, "apiStatus", newStat.toString())) {
		Logger("API Status is: (${newStat.toString().capitalize()}) | Previous State: (${curStat.toString().capitalize()})")
		sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true)
	}
}

def lastUpdatedEvent(sendEvt=false) {
	def now = new Date()
	def tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
	def lastUpd = device.currentState("lastConnection")?.value
	def lastDt = "${tf?.format(now)}"
	state?.lastUpdatedDt = lastDt?.toString()
	state?.lastUpdatedDtFmt = formatDt(now)
	if(sendEvt) {
		// Logger("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
		sendEvent(name: 'lastConnection', value: lastDt?.toString(), displayed: false, isStateChange: true)
	}
}

/************************************************************************************************
 |										LOGGING FUNCTIONS										|
 *************************************************************************************************/

def lastN(String input, n) {
	return n > input?.size() ? input : input[-n..-1]
}

def Logger(msg, logType = "debug") {
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
