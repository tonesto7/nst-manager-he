/**
 *  Nest Eventstream
 *	Copyright (C) 2018, 2019 Anthony S..
 *	Author: Anthony Santilli (@tonesto7)
 *  Modified: 08/05/2020
 */

import java.text.SimpleDateFormat
import groovy.json.*
import java.security.MessageDigest
import groovy.transform.Field

static String devVer() { return "2.0.9" }
static Boolean eric() { return false }

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
	if((Boolean)state.streamRunning) {
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
	state.remove('savedmymeta')
	state.remove('savedmystruct')
	state.remove('savedmythermostats')
	state.remove('savedmythermostatsorig')
	state.remove('savedmyprotects')
	state.remove('savedmyprotectsorig')
	state.remove('savedmycamerasorig')
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

@SuppressWarnings('unused')
def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getDevTypeId() { return device?.getDevTypeId() }

@SuppressWarnings('unused')
def getDeviceStateData() {
	return getState()
}

@SuppressWarnings('unused')
static Boolean isStreamDevice() { return true }

// called by parent
void blockStreaming(Boolean val) {
	if((Boolean)state.blockEventStreaming != val) {
		Logger("blockStreaming(${val})")
	}
	state.blockEventStreaming = val
}

// called by parent
void streamStart() {
	Logger("streamStart()")
	if((Boolean)state.streamRunning) {
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
		allEventCountFLD = 0L
		eventCountFLD = 0L
		state.sentForceNull = false
		savedFLDmymeta = (String)null
		savedFLDmymetaD = [:]
		savedFLDmystruct = (String)null
		savedFLDmystructD = [:]
		savedFLDmythermostats = [:]
		savedFLDmythermostatsD = [:]
		savedFLDmythermostatsorig = [:]
		savedFLDmythermostatsorigD = [:]
		savedFLDmyprotects = [:]
		savedFLDmyprotectsD = [:]
		savedFLDmyprotectsorig = [:]
		savedFLDmyprotectsorigD = [:]
		savedFLDmycamerasorig = [:]
		savedFLDmycamerasorigD = [:]
		//state.camSave = [:]
		camSaveFLD = [:]
		//state.lastEventData = [:]
		lastEventDataFLD = [:]
		if(!logEnable) {
			device.updateSetting("logEnable",[value:"true",type:"bool"])
			runIn(900, logsOff)
		}
		lastUpdatedEvent(true)
		//eventStreamConnect(url, "Bearer ${tkn}")
		interfaces.eventStream.connect(url, [headers:[Authorization:"Bearer ${tkn}".toString()]])
	} else {
		Logger("Unable to start stream... Missing Token: ($tkn) or API Url: [${url}] or structure [${state.structure}]", "warn")
		setStreamStatusVal(false)
	}
}

// called by parent
void streamStop() {
	Logger("Stream Stopping...")
	Boolean sendNull = false
	if((Boolean)state.streamRunning) {
		sendNull = true
	}
	setStreamStatusVal(false)
	blockStreaming(true)
	if(sendNull) { sendRecent(true) }
	//eventStreamClose()
	interfaces.eventStream.close()
}

@Field static Long allEventCountFLD 
@Field static Long eventCountFLD 
@Field static Map lastEventDataFLD
@Field static String savedFLDmymeta = (String)null
@Field static Map savedFLDmymetaD = [:]
@Field static String savedFLDmystruct = (String)null
@Field static Map savedFLDmystructD = [:]
@Field static Map savedFLDmythermostats = [:]
@Field static Map savedFLDmythermostatsD = [:]
@Field static Map savedFLDmythermostatsorig = [:]
@Field static Map savedFLDmythermostatsorigD = [:]
@Field static Map savedFLDmyprotects = [:]
@Field static Map savedFLDmyprotectsD = [:]
@Field static Map savedFLDmyprotectsorig = [:]
@Field static Map savedFLDmyprotectsorigD = [:]
@Field static Map savedFLDmycamerasorig = [:]
@Field static Map savedFLDmycamerasorigD = [:]
@Field static Map camSaveFLD

void parse(String description) {
	//log.warn "Event: ${description}"
//	try {
		if (!(Boolean)state.blockEventStreaming && description) {
			def data = new JsonSlurper().parseText(description)
			if (data?.size()) {
				allEventCountFLD = allEventCountFLD ? allEventCountFLD + 1L : 1L
				//Logger("Stream Event Received...", "info")
				Boolean chgd = false
				Boolean somechg = false
				if(state.structure) {
					//def theRawEvent = new JsonSlurper().parseText(description as String)
					//def theRawEvent = new JsonSlurper().parseText(JsonOutput.toJson(data))
					Map theRawEvent = [:] + data // make a copy

					// This is the "cleaned up" event we will send to NST manager.
					LinkedHashMap theNewEvent = [:]
					theNewEvent.path = theRawEvent?.path
					theNewEvent.data = [:]
					theNewEvent.data.metadata = [:]
					theNewEvent.data.devices = [:]
					theNewEvent.data.structures = [:]

					LinkedHashMap mydata = data?.data as Map
					if(!mydata) { Logger("No Data in mydata", "warn") }

					LinkedHashMap tempmymeta = (Map)mydata?.metadata

					Boolean chgFound = true
					String tchksum = (String)null
					List ch = []

					if(tempmymeta) {
						theNewEvent.data.metadata = [:] + (Map)theRawEvent.data.metadata
						tchksum = generateMD5_A(tempmymeta.toString())
						if(tchksum == (String)savedFLDmymeta) { chgFound = false }
						if(chgFound){
							ch = getChanges(tempmymeta, savedFLDmymetaD, "/metatdata", "metadata")
							if(!ch) chgFound=false
						}
					}
					if(tempmymeta && ( !savedFLDmymeta || chgFound )) {
						chgd = true
						savedFLDmymeta = tchksum
						savedFLDmymetaD=tempmymeta
						if(eric()){
							Logger("tempmymeta changed", "info")
							Logger("chgFound ${ch}", "info")
							//Logger("tempmymeta ${tempmymeta.toString()}", "info")
							//Logger("savedFLDmyymeta ${savedFLDmymeta.toString()}", "info")
						}
					}

					if(!mydata?.structures) { Logger("No Data in structures", "warn"); return }
					LinkedHashMap mystruct = mydata.structures?."${state.structure}"
					if(!mystruct) { /* Logger("No Data in structure ${state.structure}", "warn");*/ return }

					//theNewEvent.data.structures."${state.structure}" = [:] + mystruct
					theNewEvent.data.structures = [:] + (Map)mydata.structures

					ch=[]
					tchksum = (String)null
					chgFound = true
					Map st0 = [:] + mystruct
					Map st1 = savedFLDmystructD
					if(st0) {
						st0.wheres = [:]
						tchksum = generateMD5_A(st0.toString())
						if(tchksum == (String)savedFLDmystruct) { chgFound = false }
						if(chgFound){
							ch = getChanges(st0, st1, "/structure", "structure")
							if(!ch) chgFound=false
						}
					} else {
						Logger("No Data in structures ${state.structure}", "warn")
						return
					}

					if(st0 && ( !savedFLDmystruct || chgFound)) {
						chgd = true
						savedFLDmystruct = tchksum
						savedFLDmystructD = st0
						if(eric()){
							Logger("mystruct changed structure ${state.structure}", "info")
							Logger("mystruct chgFound ${ch}", "info")
						}
					}

					if(mystruct?.thermostats) {
						theNewEvent.data.devices.thermostats = [:]
						Integer tlen = mystruct.thermostats.size()
						for (i = 0; i < tlen; i++) {
							String t1 = (String)((List)mystruct.thermostats)[i]

							ch=[]
							Map adjT1 = (Map)mydata.devices.thermostats[t1]
							if(!adjT1) { Logger("No Data in thermostat ${i} ${t1}", "warn"); return }
							theNewEvent.data.devices.thermostats."${t1}" = adjT1
	
							Map t0 = savedFLDmythermostatsorigD ?: [:]
							Map adjT2= t0?."${t1}" ? (Map)t0[t1] : [:]

							String prevCheckSum = (String)null
							t0 = savedFLDmythermostatsorig ?: [:]
							if(t0?."${t1}") { prevCheckSum = (String)t0[t1] }
							//Logger("thermostat ${i} ${t1} adjT1 ${adjT1}", "debug")
							//Logger("thermostat ${i} ${t1} prevCheckSum ${prevCheckSum}", "debug")

							tchksum = (String)null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								if(chgFound){
									ch = getChanges(adjT1, adjT2, "/thermostats", "tstat")
									if(!ch) chgFound=false
								}
							}
							if(adjT1 && ( !prevCheckSum || chgFound )) {
								t0 = savedFLDmythermostatsorig
								t0[t1] = tchksum
								savedFLDmythermostatsorig = t0
								t0 = savedFLDmythermostatsorigD ?: [:]
								t0[t1] = adjT1
								savedFLDmythermostatsorigD = t0
								if(eric()){
									Logger("thermostat SOME changes ${t1}", "info")
									Logger("tstat SOME chgFound ${ch}", "info")
								}
								somechg = true
							}

							prevCheckSum = (String)null
							t0 = savedFLDmythermostats ?: [:]
							if(t0?."${t1}") { prevCheckSum = (String)t0[t1] }

							t0 = savedFLDmythermostatsD ?: [:]
							Map at1 = [:]
							if(t0?."${t1}") { at1 = (Map)t0[t1] }

							ch=[]
							Map at0 = null
							tchksum = (String)null
							chgFound = true
							if(adjT1) {
								at0 = (Map)(new JsonSlurper().parseText(JsonOutput.toJson(adjT1)))
									//Map at0 = [:] + adjT1 // make a copy
								at0.last_connection = ""
									//at0.where_id = ""

									//Map at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
									//Map at1 = [:] + adjT2
									//at1.last_connection = ""
									//at1.where_id = ""
								tchksum = generateMD5_A(at0.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								if(chgFound){
									ch = getChanges(at0, at1, "/thermostats", "tstat")
									if(!ch) chgFound=false
								}
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0 = savedFLDmythermostats ?: [:]
								t0[t1] = tchksum
								savedFLDmythermostats = t0
								t0 = savedFLDmythermostatsD ?: [:]
								t0[t1] = at0
								savedFLDmythermostatsD = t0
								if(eric()){
									Logger("thermostat changed ${t1}", "info")
									Logger("tstat chgFound ${ch}", "info")
								}
								chgd = true
							}
						}
					} // else Logger("no thermostats", "warn")
					if(mystruct?.smoke_co_alarms) {
						theNewEvent.data.devices.smoke_co_alarms = [:]
						Integer tlen = mystruct.smoke_co_alarms.size()
						for (i = 0; i < tlen; i++) {
							String t1 = (String)((List)mystruct.smoke_co_alarms)[i]

							ch=[]
							Map adjT1 = (Map)mydata.devices.smoke_co_alarms[t1]
							if(!adjT1) { Logger("No Data in smoke_co_alarms ${i} ${t1}", "warn"); return }
							theNewEvent.data.devices.smoke_co_alarms."${t1}" = [:] + adjT1

							Map t0 = savedFLDmyprotectsorigD ?: [:]
							Map adjT2= t0?."${t1}" ? (Map)t0[t1] : [:]

							String prevCheckSum = (String)null
							t0 = savedFLDmyprotectsorig ?: [:]
							if(t0?."${t1}") { prevCheckSum = (String)t0[t1] }

							tchksum = (String)null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								if(chgFound){
									ch = getChanges(adjT1, adjT2, "/protects", "prot")
									if(!ch) chgFound=false
								}
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0 = savedFLDmyprotectsorig ?: [:]
								t0[t1] = tchksum
								savedFLDmyprotectsorig = t0
								t0 = savedFLDmyprotectsorigD ?: [:]
								t0[t1] = adjT1
								savedFLDmyprotectsorigD = t0
								if(eric()){
									Logger("protect SOME ${chgFound} $prevCheckSum changes ${t1}", "info")
									Logger("prot SOME chgFound ${ch}", "info")
								}
								somechg = true
							}

							prevCheckSum = (String)null // this is a checksum [:]
							t0 = savedFLDmyprotects ?: [:]
							if(t0?."${t1}") { prevCheckSum = (String)t0[t1] }

							t0 = savedFLDmyprotectsD ?: [:]
							Map at1
							String adjT2S="{}"
							if(t0?."${t1}") { adjT2S = t0[t1] }

							ch=[]
							Map at0 = [:]
							tchksum = (String)null
							if(adjT1 && chgFound) {
								chgFound = true
								at0 = (Map)(new JsonSlurper().parseText(JsonOutput.toJson(adjT1)))
									//at0 = [:] + adjT1 // make a copy
								//at0.last_connection = (String)null
								at0.remove('last_connection')
									//at0.where_id = ""
									//at1 = new JsonSlurper().parseText(JsonOutput.toJson(adjT2))
								at1 = (Map)(new JsonSlurper().parseText(adjT2S))
									//at1 = [:] + adjT2
								//at1.last_connection = (String)null
								at1.remove('last_connection')
									//at1.last_connection = ""
									//at1.where_id = ""
								tchksum = generateMD5_A(at0.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								if(chgFound){
									ch = getChanges(at0, at1, "/protects", "prot")
									if(!ch) chgFound=false
								}
							}
							if(adjT1 && ( !prevCheckSum || chgFound)) {
								t0 = savedFLDmyprotectsorig ?: [:]
								t0[t1] = tchksum
								savedFLDmyprotects = t0
								t0 = savedFLDmyprotectsD ?: [:]
								t0[t1] = JsonOutput.toJson(at0) //at0
								savedFLDmyprotectsD = t0
								if(eric()){
									Logger("protect changed ${t1}", "info")
									Logger("prot chgFound ${ch}", "info")
								}
								chgd = true
							}
						}
					} // else Logger("no protects", "warn")
					if(mystruct?.cameras) {
						theNewEvent.data.devices.cameras = [:]
						Map camSave = [:]
						Integer tlen = mystruct.cameras.size()
						for (i = 0; i < tlen; i++) {
							String t1 = ((List)mystruct.cameras)[i]

							ch=[]
							Map adjT1 = (Map)mydata.devices.cameras[t1]
							if(!adjT1) { Logger("No Data in cameras ${i} ${t1}", "warn"); return }

							def t0 = savedFLDmycamerasorigD ?: [:]
							def adjT2= t0?."${t1}" ? (Map)t0[t1] : [:]

							t0 = savedFLDmycamerasorig ?: [:]
							String prevCheckSum  = (String)null // = [:]   this is a checksum [:]
							if(t0?."${t1}") { prevCheckSum = (String)t0[t1] }

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

							tchksum = (String)null
							chgFound = true
							if(adjT1) {
								tchksum = generateMD5_A(adjT1.toString())
								if(tchksum == prevCheckSum) { chgFound = false }
								if(chgFound){
									ch = getChanges(adjT1, adjT2, "/cameras", "cam")
									if(!ch) chgFound=false
								}
							}
							if(adjT1 && (!prevCheckSum || chgFound)) {     //adjT1.toString() != adjT2.toString()) {
								t0 = savedFLDmycamerasorig ?: [:]
								t0[t1] = tchksum
								savedFLDmycamerasorig = t0
								if(!myisonline || !myisstreaming) {
									somechg = true
									chgFound = false
								}
								t0 = savedFLDmycamerasorigD ?: [:]
								t0[t1] = adjT1
								savedFLDmycamerasorigD = t0
							}

/*
							t0 = savedFLDmycameras ?: [:]
							prevCheckSum = //[:]
							if(t0?."${t1}") { prevCheckSum = t0[t1] }

							ch=[]
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
								//savedFLDmycameras = t0
								chgd = true
								if(eric()){
									Logger("camera changed ${t1}", "info")
									Logger("camera chgFound ${ch}", "info")
								}
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
							camSaveFLD = null
							camSaveFLD = camSave
							//Logger("updating camSave", "warn")
						}
					}  else {
						//if(state.camSave) {
						if(camSaveFLD) {
							if(eric())Logger("using camSave", "warn")
							theNewEvent.data.devices.cameras = [:]
							//theNewEvent.data.devices.cameras = state.camSave
							theNewEvent.data.devices.cameras = camSaveFLD
						} //else Logger("no cameras", "warn")
					}

					//state.lastEventData = theNewEvent
					lastEventDataFLD = null
					lastEventDataFLD = theNewEvent
				}
				else {
					Logger("no state.structure", "error")
					chgd = true
				}
				setStreamStatusVal(true)
				if(!chgd && somechg) {
					if(!(Boolean)state.runInSlowActive) {
						state.runInSlowActive = true
						Logger("scheduling event", "info")
						runIn(95, "sendRecent", [overwrite: true])
					}
				}
				if(chgd) {
					if((Boolean)state.runInSlowActive) {
						Logger("unscheduling event", "info")
						unschedule("sendRecent")  // or runIn(6000, "sendRecent", [overwrite: true])
						state.runInSlowActive = false
					}
					runIn(2, "sendRecent", [overwrite: true])
					//sendRecent()
				}
			}
		} else {
			savedFLDmymeta = (String)null
			savedFLDmymetaD = [:]
			savedFLDmystruct = (String)null
			savedFLDmystructD = [:]
			savedFLDmythermostats = [:]
			savedFLDmythermostatsD = [:]
			savedFLDmythermostatsorig = [:]
			savedFLDmythermostatsorigD = [:]
			savedFLDmyprotects = [:]
			savedFLDmyprotectsD = [:]
			savedFLDmyprotectsorig = [:]
			savedFLDmyprotectsorigD = [:]
			savedFLDmycamerasorig = [:]
			savedFLDmycamerasorigD = [:]
			//state.camSave = [:]
			camSaveFLD = [:]
		}
/*
	} catch (ex) {
		log.error "parse Error: ${ex.message}" // no need to restart stream here if error
	}
*/
}

String generateMD5_A(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

List getChanges(mapA, mapB, String headstr, String objType=(String)null) {
	def t0 = mapA
	def t1 = mapB
	def left = t0
	def right = t1
	List itemsChgd = []
	//Logger("getChanges ${headstr} t0 ${t0}", "info")
	//Logger("getChanges ${headstr} t1 ${t1}", "info")
	if (left instanceof Map) {
		String[] leftKeys = left.keySet()
		String[] rightKeys = right?.keySet()
		leftKeys.each {
			if( left[it] instanceof Map ) {
				Map nr= right && right[it] ? (Map)right[it] : [:]
				List chgs = getChanges( left[it], nr, "${headstr}/${it}".toString(), objType )
				if(chgs && objType) {
					itemsChgd += chgs
				}
			} else {
				String nr= right && right[it] ? right[it].toString() : (String)null
				String nl= left && left[it] ? left[it].toString() : (String)null
				if (nl != nr) {
       					 if(objType) {
//						Logger("getChanges ${headstr} IT: ${it}  LEFT: ${left[it]}   RIGHT:${right[it]}")
						itemsChgd.push("${it} ${left[it]} $nr".toString())
       				 	}
				}
			}
		}
		if(itemsChgd.size()) {
			//Logger("returning items ${itemsChgd}", "info")
			return itemsChgd
		}
	}
	return itemsChgd
}

void sendRecent(Boolean forceNull=false) {
	//t0 = state.lastEventData
	Map t0 = lastEventDataFLD
	state.runInSlowActive = false
	if(t0 || forceNull) {
		if(forceNull && !(Boolean)state.sentForceNull) {
			t0 = [:]
			state.sentForceNull = true
			Logger("Forced Null sent Event # ${eventCountFLD} / ${allEventCountFLD}")
		} else {
			if(forceNull) {
				Logger("Skipping Forced Null sent Event # ${eventCountFLD} / ${allEventCountFLD}", "warn")
				return
			}
			eventCountFLD = eventCountFLD ? eventCountFLD + 1L : 1L
			Logger("Sent Event Data Event # ${eventCountFLD} / ${allEventCountFLD}", "info")
		}
		parent.receiveEventData(t0)
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

@SuppressWarnings('unused')
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
	def tz
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
	SimpleDateFormat tf = new SimpleDateFormat("MMM d, yyyy - h:mm:ss a")
	tf.setTimeZone(getTimeZone())
//	String lastUpd = device.currentState("lastConnection")?.value
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

void Logger(GString msg, String logType = "debug") {
	Logger(msg.toString(), logType)
}

void Logger(String msg, String logType = "debug") {
	if(!logEnable || !msg) { return }
	if((Boolean)state.enRemDiagLogging == null) {
		state.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
		if((Boolean)state.enRemDiagLogging == null) {
			state.enRemDiagLogging = false
		}
		//log.debug "set enRemDiagLogging to ${state.enRemDiagLogging}"
	}
        if((Boolean)state.enRemDiagLogging) {
		String smsg = "${device.displayName} (v${devVer()}) | ${msg}".toString()
		String theId = lastN((String)device.getId().toString(),5)
		Boolean a=parent.saveLogtoRemDiagStore(smsg, logType, "EventStream-${theId}".toString())
        } else {
		switch (logType) {
		case "trace":
			log.trace msg
			break
		case "debug":
			log.debug msg
			break
		case "info":
			log.info msg
			break
		case "warn":
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

//This will Print logs from the parent app when added to parent method that the child calls
def log(String message, String level = "trace") {
	Logger("PARENT_Log>> " + message, level)
	return null // always call child interface with a return value
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

def getSettingVal(String svar) {
	if(svar == null) { return settings }
	return settings[svar] ?: null
}

String formatDt(Date dt) {
	SimpleDateFormat tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(dt)
}
