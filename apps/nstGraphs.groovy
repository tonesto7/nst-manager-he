/************************************************************************************************
|	Application Name: NST Graphs								|
|	Copyright (C) 2018, 2019								|
|	Authors: Anthony S. (@tonesto7), Eric S. (@nh.schottfam)				|
|												|
|	Updated 5/3/2019									|
|	License Info: https://github.com/tonesto7/nest-manager/blob/master/app_license.txt	|
|************************************************************************************************/

import groovy.json.*
import java.text.SimpleDateFormat
import groovy.time.*
import groovy.transform.Field

definition(
	name: "NST Graphs",
	namespace: "tonesto7",
	author: "Anthony S.",
	parent: "tonesto7:NST Manager",
	description: "This App is used to display device graphs for NST Manager",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	singleInstance: true,
	oauth: true
)

def appVersion() { "2.0.1" }

preferences {
	page(name: "startPage")
	page(name: "mainAutoPage")
	page(name: "notAllowedPage")
}

mappings {
	path("/deviceTiles")	{action: [GET: "renderDeviceTiles"]}
	path("/tstatTiles")	{action: [GET: "getTstatTiles"]}
	path("/protTiles")	{action: [GET: "getProtTiles"]}
	path("/weatherTile")	{action: [GET: "getWeatherTile"]}
	path("/getTile/:dni")	{action: [GET: "getTile"]}
}

def startPage() {
	//log.info "startPage"

	if(!state?.autoTyp) { Logger("nothing is set startPage") }

	if(parent) {
		Boolean t0 = parent.getStateVal("ok2InstallAutoFlag")
		if( /* !state?.isInstalled && */ t0 != true) {
			//Logger("Not installed ${t0}")
			notAllowedPage()
		} else {
			state?.isParent = false
	if(!state?.access_token) { getAccessToken() }
	if(!state?.access_token) { enableOauth(); getAccessToken() }
			mainAutoPage()
		}
	} else {
		notAllowedPage()
	}
}

def notAllowedPage () {
	dynamicPage(name: "notAllowedPage", title: "This install Method is Not Allowed", install: false, uninstall: true) {
		section() {
			paragraph imgTitle(getAppImg("disable_icon2.png"), paraTitleStr("WE HAVE A PROBLEM!\n\nNST Automations can't be directly installed.\n\nPlease use the Nest Integrations App to configure them.")), required: true, state: null
		}
	}
}

def mainAutoPage() {
	def t0 = getTemperatureScale()?.toString()
	state?.tempUnit = (t0 != null) ? t0 : state?.tempUnit
	if(!state?.autoDisabled) { state.autoDisabled = false }

		return dynamicPage(name: "mainAutoPage", title: pageTitleStr("Automation Configuration"), uninstall: true, install: true, nextPage:null ) {
			section() {
				if(settings?.autoDisabledreq) {
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is currently disabled!\nTurn it back on to to make changes or resume operation")), required: true, state: null
				} else {
					if(getIsAutomationDisabled()) { paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is still disabled!\nPress Next and Done to Activate this Automation Again")), state: "complete" }
				}
				if(!getIsAutomationDisabled()) {
					t1 = ""
					def tstats = parent.getSettingVal("thermostats")
					if(tstats) {
						def vtstats = parent.getStateVal("vThermostats")
						def foundvTstats
						if(vtstats) {
							foundvTstats = vtstats?.each { dni ->
								def mydni = parent.getNestvStatDni(dni).toString()
								tstats << mydni
								}
						}
						t1 += "\n • With Thermostats (${tstats.size()})"
					}
					def prots = parent.getSettingVal("protects")
					if(prots) {
						t1 += "\n • With Protects (${prots.size()})"
					}
					def weather = parent.getSettingVal("weatherDevice")
					if(weather) {
						t1 += "\n • With Weather Device"
					}
					if(t1 != "") {
						t1 = "Charts On:" + t1
						paragraph sectionTitleStr(t1)

						if(tstats.size() > 1 || (tstats.size() > 0 && weather.size() > 0)) {
							def myUrl = getAppEndpointUrl("deviceTiles")
							def myLUrl = getLocalEndpointUrl("deviceTiles")
							def myStr = """ <a href="${myUrl}" target="_blank">All Devices</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
					}
					if(tstats) {
						if(tstats.size() > 1) {
							def myUrl = getAppEndpointUrl("tstatTiles")
							def myLUrl = getLocalEndpointUrl("tstatTiles")
							def myStr = """ <a href="${myUrl}" target="_blank">All Thermostats</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
						//def sUrl = "${fullApiServerUrl("")}"
						def foundTstats = tstats?.collect { dni ->
							def d1 = parent.getDevice(dni)
							def myUrl = getAppEndpointUrl("getTile/${dni}")
							def myLUrl = getLocalEndpointUrl("getTile/${dni}")
							//def myUrl = "${sUrl}" + "/getTile/${dni}" + "?access_token=${state.access_token}"
//Logger("mainAuto sUrl: ${sUrl}   myUrl: ${myUrl}")
							def myStr = """ <a href="${myUrl}" target="_blank">${d1.label ?: d1.name}</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
					}
					if(prots) {
						if(prots.size() > 1) {
							def myUrl = getAppEndpointUrl("protTiles")
							def myLUrl = getLocalEndpointUrl("protTiles")
							def myStr = """ <a href="${myUrl}" target="_blank">All Protects</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
						//def sUrl = "${fullApiServerUrl("")}"
						def foundTstats = prots?.collect { dni ->
							def d1 = parent.getDevice(dni)
							def myUrl = getAppEndpointUrl("getTile/${dni}")
							def myLUrl = getLocalEndpointUrl("getTile/${dni}")
							//def myUrl = "${sUrl}" + "/getTile/${dni}" + "?access_token=${state.access_token}"
//Logger("mainAuto sUrl: ${sUrl}   myUrl: ${myUrl}")
							def myStr = """ <a href="${myUrl}" target="_blank">${d1.label ?: d1.name}</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
					}
					if(weather) {
						def myUrl = getAppEndpointUrl("weatherTile")
						def myLUrl = getLocalEndpointUrl("weatherTile")
						def myStr = """ <a href="${myUrl}" target="_blank">${weather.label ?: weather..name}</a> <a href="${myLUrl}" target="_blank">(local)</a> """
						paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
					}
				}
			}
			section(sectionTitleStr("Automation Options:")) {
				input "autoDisabledreq", "bool", title: imgTitle(getAppImg("disable_icon2.png"), inputTitleStr("Disable this Automation?")), required: false, defaultValue: false /* state?.autoDisabled */, submitOnChange: true
				setAutomationStatus()

				input("showDebug", "bool", title: imgTitle(getAppImg("debug_icon.png"), inputTitleStr("Debug Option")), description: "Show ${app?.name} Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true)
				if(showDebug) {
					input("advAppDebug", "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose Logs?")), required: false, defaultValue: false, submitOnChange: true)
				} else {
					settingUpdate("advAppDebug", "false", "bool")
				}
			}
			section(sectionTitleStr("Application Security")) {
				paragraph title:"What does resetting do?", "If you share a url with someone and want to remove their access you can reset your token and this will invalidate any URL you shared and create a new one for you.  This will require any use in dashboards to be updated to the new URL."
				input (name: "resetAppAccessToken", type: "bool", title: "Reset Access Token?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("reset_icon.png"))
				resetAppAccessToken(settings?.resetAppAccessToken == true)
			}
			section(sectionTitleStr("Automation Name:")) {
				def newName = getAutoTypeLabel()
				if(!app?.label) { app?.updateLabel("${newName}") }
				label title: imgTitle(getAppImg("name_tag_icon.png"), inputTitleStr("Label this Automation: Suggested Name: ${newName}")), defaultValue: "${newName}", required: true //, wordWrap: true
				if(!state?.isInstalled) {
					paragraph "Make sure to name it something that you can easily recognize."
				}
			}
		}
}

private isHubitat(){
	return hubUID != null
}

def installed() {
	log.debug "${app.getLabel()} Installed with settings: ${settings}"		// MUST BE log.debug
	if(isHubitat() && !app.id) return
	initialize()
	return true
}

def updated() {
	log.debug "${app.getLabel()} Updated...with settings: ${settings}"
	state?.isInstalled = true
	def appLbl = getCurAppLbl()
	if(appLbl?.contains("Graphs")) {
		if(!state?.autoTyp) { state.autoTyp = "chart" }
	}
	initialize()
	state?.lastUpdatedDt = getDtNow()
	return true
}

def uninstalled() {
	log.debug "uninstalled"
	uninstAutomationApp()
}

def initialize() {
	log.debug "${app.label} Initialize..."		// Must be log.debug
	state.autoTyp = "chart"
	resetVars()
	if(!state?.isInstalled) { state?.isInstalled = true }
	Boolean settingsReset = parent.getSettingVal("resetAllData")
	//if(state?.resetAllData || settingsReset) {
	//	if(fixState()) { return }	// runIn of fixState will call initAutoApp()
	//}

	runIn(6, "initAutoApp", [overwrite: true])
}

def resetVars() {
	stateRemove("evalSched")
	stateRemove("haveWeather")
	stateRemove("obs")
	stateRemove("detailEventHistory")
	stateRemove("detailExecutionHistory")
}

def initAutoApp() {
	if(settings["chartFlag"]) {
		state.autoTyp = "chart"
	}
	//initHistoryStore([id:200])
	unschedule()
	unsubscribe()
	setAutomationStatus()

	subscribeToEvents()
	scheduler()

	app.updateLabel(getAutoTypeLabel())
	LogAction("Automation Label: ${getAutoTypeLabel()}", "info", true)

//ERS
	settingUpdate("showDebug", "true", "bool")
	settingUpdate("advAppDebug", "true", "bool")
	stateRemove("enRemDiagLogging") // cause recheck

	scheduleAutomationEval(30)
	if(showDebug || advAppDebug) { runIn(1800, logsOff) }

	checkCleanups()

//ERS
	//revokeAccessToken()

/*
	def devTilesUrl = getAppEndpointUrl("deviceTiles")
	def tstatTilesUrl = getAppEndpointUrl("tstatTiles")
	def weatherTilesUrl = getAppEndpointUrl("weatherTile")

Logger("initAutoApp: devTile: ${devTilesUrl}")
Logger("initAutoApp: tstatTile: ${tstatTilesUrl}")
Logger("initAutoApp: weatherTile: ${weatherTilesUrl}")
*/
}

def subscribeToEvents() {
	def weather = parent.getSettingVal("weatherDevice")
	if(weather) {
		subscribe(weather, "temperature", automationGenericEvt)
		subscribe(weather, "humidity", automationGenericEvt)
	} else { LogAction("No weather device found", "error", true) }

	def tstats = parent.getSettingVal("thermostats")
	def foundTstats
	if(tstats) {
		foundTstats = tstats?.collect { dni ->
			def d1 = parent.getDevice(dni)
			if(d1) {
				//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)
				tSubscribe(d1)
			}
			return d1
		}
	}
	def vtstats = parent.getStateVal("vThermostats")
	def foundvTstats
	if(vtstats) {
		foundvTstats = vtstats?.each { dni ->
			def mydni = parent.getNestvStatDni(dni).toString()
			def d1 = parent.getDevice(mydni)
			if(d1) {
				//LogAction("Found: ${d1?.displayName} with (Id: ${mydni?.key})", "debug", false)
				tSubscribe(d1)
			} else { LogAction(" vstat NOT found $dni", "error", true) }
		}
	}

}

def tSubscribe(d1) {
	subscribe(d1, "temperature", automationGenericEvt)
	subscribe(d1, "humidity", automationGenericEvt)
	subscribe(d1, "thermostatOperatingState", automationGenericEvt)
	subscribe(d1, "thermostatFanMode", automationGenericEvt)
	subscribe(location, "mode", automationGenericEvt)
	if(getCanCool(d1)) {
		subscribe(d1, "coolingSetpoint", automationGenericEvt)
	}
	if(getCanHeat(d1)) {
		subscribe(d1, "heatingSetpoint", automationGenericEvt)
	}
}

def scheduler() {
//	"runEvery${state.poll}Minutes"(poll)
	runEvery15Minutes(resetVars)
}

def uninstAutomationApp() {
}

def strCapitalize(str) {
	return str ? str?.toString().capitalize() : null
}

def automationGenericEvt(evt) {
	def startTime = now()
	def eventDelay = startTime - evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "info", false)

	doTheEvent(evt)
}

def doTheEvent(evt) {
	if(getIsAutomationDisabled()) { return }
	else {
		scheduleAutomationEval()
		storeLastEventData(evt)
	}
}

def storeLastEventData(evt) {
	if(evt) {
		def newVal = ["name":evt.name, "displayName":evt.displayName, "value":evt.value, "date":formatDt(evt.date), "unit":evt.unit]
		state?.lastEventData = newVal
		//log.debug "LastEvent: ${state?.lastEventData}"

		def list = state?.detailEventHistory ?: []
		def listSize = 15
		if(list?.size() < listSize) {
			list.push(newVal)
		}
		else if(list?.size() > listSize) {
			def nSz = (list?.size()-listSize) + 1
			def nList = list?.drop(nSz)
			nList?.push(newVal)
			list = nList
		}
		else if(list?.size() == listSize) {
			def nList = list?.drop(1)
			nList?.push(newVal)
			list = nList
		}
		if(list) { state?.detailEventHistory = list }
	}
}

def storeExecutionHistory(val, method = null) {
	//log.debug "storeExecutionHistory($val, $method)"
	//try {
		if(method) {
			LogTrace("${method} Execution Time: (${val} milliseconds)")
		}
		//if(method in ["watchDogCheck", "checkNestMode", "schMotCheck"]) {
			state?.autoExecMS = val ?: null
			def list = state?.evalExecutionHistory ?: []
			def listSize = 20
			list = addToList(val, list, listSize)
			if(list) { state?.evalExecutionHistory = list }
		//}
/*
		if(!(method in ["watchDogCheck", "checkNestMode"])) {
			def list = state?.detailExecutionHistory ?: []
			def listSize = 30
			list = addToList([val, method, getDtNow()], list, listSize)
			if(list) { state?.detailExecutionHistory = list }
		}
*/
/*
	} catch (ex) {
		log.error "storeExecutionHistory Exception:", ex
		//parent?.sendExceptionData(ex, "storeExecutionHistory", true, getAutoType())
	}
*/
}

def addToList(val, list, listSize) {
	if(list?.size() < listSize) {
		list.push(val)
	} else if(list?.size() > listSize) {
		def nSz = (list?.size()-listSize) + 1
		def nList = list?.drop(nSz)
		nList?.push(val)
		list = nList
	} else if(list?.size() == listSize) {
		def nList = list?.drop(1)
		nList?.push(val)
		list = nList
	}
	return list
}

def setAutomationStatus(upd=false) {
	Boolean myDis = (settings?.autoDisabledreq == true)
	Boolean settingsReset = (parent.getSettingVal("disableAllAutomations") == true)
	Boolean storAutoType = getAutoType() == "storage" ? true : false
	if(settingsReset && !storAutoType) {
		if(!myDis && settingsReset) { LogAction("setAutomationStatus: Nest Integrations forcing disable", "info", true) }
		myDis = true
	} else if(storAutoType) {
		myDis = false
	}
	if(!getIsAutomationDisabled() && myDis) {
		LogAction("Automation Disabled at (${getDtNow()})", "info", true)
		state?.autoDisabledDt = getDtNow()
	} else if(getIsAutomationDisabled() && !myDis) {
		LogAction("Automation Enabled at (${getDtNow()})", "info", true)
		state?.autoDisabledDt = null
	}
	state?.autoDisabled = myDis
	if(upd) { app.update() }
}

def defaultAutomationTime() {
	return 5
}

def scheduleAutomationEval(schedtime = defaultAutomationTime()) {
	def theTime = schedtime
	if(theTime < defaultAutomationTime()) { theTime = defaultAutomationTime() }
	def autoType = getAutoType()
	def random = new Random()
	def random_int = random.nextInt(6)  // this randomizes a bunch of automations firing at same time off same event
	def waitOverride = false
	switch(autoType) {
		case "chart":
			if(theTime == defaultAutomationTime()) {
				theTime += random_int
			}
			def schWaitVal = settings?.schMotWaitVal?.toInteger() ?: 60
			if(schWaitVal > 120) { schWaitVal = 120 }
			def t0 = getAutoRunSec()
			if((schWaitVal - t0) >= theTime ) {
				theTime = (schWaitVal - t0)
				waitOverride = true
			}
			//theTime = Math.min( Math.max(theTime,defaultAutomationTime()), 120)
			break
	}
	if(!state?.evalSched) {
		runIn(theTime, "runAutomationEval", [overwrite: true])
		state?.autoRunInSchedDt = getDtNow()
		state.evalSched = true
		state.evalSchedLastTime = theTime
	} else {
		def str = "scheduleAutomationEval: "
		def t0 = state?.evalSchedLastTime
		if(t0 == null) { t0 = 0 }
		def timeLeftPrev = t0 - getAutoRunInSec()
		if(timeLeftPrev < 0) { timeLeftPrev = 100 }
		def str1 = " Schedule change: from (${timeLeftPrev}sec) to (${theTime}sec)"
		if(timeLeftPrev > (theTime + 5) || waitOverride) {
			if(Math.abs(timeLeftPrev - theTime) > 3) {
				runIn(theTime, "runAutomationEval", [overwrite: true])
				LogTrace("${str}Performing${str1}")
				state?.autoRunInSchedDt = getDtNow()
				state.evalSched = true
				state.evalSchedLastTime = theTime
			}
		} else { LogTrace("${str}Skipping${str1}") }
	}
}

def getAutoRunSec() { return !state?.autoRunDt ? 100000 : GetTimeDiffSeconds(state?.autoRunDt, null, "getAutoRunSec").toInteger() }

def getAutoRunInSec() { return !state?.autoRunInSchedDt ? 100000 : GetTimeDiffSeconds(state?.autoRunInSchedDt, null, "getAutoRunInSec").toInteger() }

def runAutomationEval() {
	LogTrace("runAutomationEval")
	def execTime = now()
	def autoType = getAutoType()
	state.evalSched = false
	state?.evalSchedLastTime = null
	switch(autoType) {
		case "chart":
			def weather = parent.getSettingVal("weatherDevice")
			if(weather) {
				getSomeWData(weather)
			}

			def tstats = parent.getSettingVal("thermostats")
			def foundTstats
			if(tstats) {
				foundTstats = tstats?.collect { dni ->
					def d1 = parent.getDevice(dni)
					if(d1) {
						//LogAction("Found: ${d1?.displayName} with (Id: ${dni})", "debug", false)
						getSomeData(d1)
					}
					return d1
				}
			}

			def vtstats = parent.getStateVal("vThermostats")
			def foundvTstats
			if(vtstats) {
				foundvTstats = vtstats?.collect { dni ->
					def mydni = parent.getNestvStatDni(dni).toString()
					def d1 = parent.getDevice(mydni)
					if(d1) {
						//LogAction("Found: ${d1?.displayName} with (Id: ${mydni})", "debug", false)
						getSomeData(d1)
					}
					return d1
				}
			}
			break
		default:
			LogAction("runAutomationEval: Invalid Option Received ${autoType}", "warn", true)
			break
	}
	storeExecutionHistory((now()-execTime), "runAutomationEval")
}

def getCurAppLbl() { return app?.label?.toString() }

def appLabel()	{ return "NST Graphs" }
def appName()		{ return "${appLabel()}" }

def getAutoTypeLabel() {
	//LogTrace("getAutoTypeLabel()")
	def type = state?.autoTyp
	def appLbl = getCurAppLbl()
	def newName = appName() == "${appLabel()}" ? "NST Graphs" : "${appName()}"
	def typeLabel = ""
	def newLbl
	def dis = (getIsAutomationDisabled() == true) ? "\n(Disabled)" : ""

	typeLabel = "Nest Location ${location.name} Graphs"

//Logger("getAutoTypeLabel: ${type} ${appLbl} ${appName()} ${appLabel()} ${typeLabel}")

	if(appLbl != "" && appLbl && appLbl != "Nst Graphs" && appLbl != "${appLabel()}") {
		if(appLbl?.contains("\n(Disabled)")) {
			newLbl = appLbl?.replaceAll('\\\n\\(Disabled\\)', '')
		} else {
			newLbl = appLbl
		}
	} else {
		newLbl = typeLabel
	}
	return "${newLbl}${dis}"
}

//ERS
def checkCleanups() {
	def inuse = []
	def weather = parent.getSettingVal("weatherDevice")
	if(weather) {
		inuse += weather.id
	}

	def tstats = parent.getSettingVal("thermostats")
	def foundTstats
	if(tstats) {
		foundTstats = tstats?.collect { dni ->
			def d1 = parent.getDevice(dni)
			if(d1) {
				inuse += d1.id
				//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)
			}
		}
	}
	def vtstats = parent.getStateVal("vThermostats")
	def foundvTstats
	if(vtstats) {
		foundvTstats = vtstats?.collect { dni ->
			def mydni = parent.getNestvStatDni(dni).toString()
			def d1 = parent.getDevice(mydni)
			if(d1) {
				inuse += d1.id
				//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)
			}
		}
	}

	def data = []
	def regex1 = /Wtoday/
	["Wtoday"]?.each { oi->
		state?.each { if(it?.key?.toString().startsWith(oi)) {
				data?.push(it?.key.replaceAll(regex1, ""))
			}
		}
	}
	def regex2 = /thermStor/
	["thermStor"]?.each { oi->
		state?.each { if(it?.key?.toString().startsWith(oi)) {
				data?.push(it?.key.replaceAll(regex2, ""))
			}
		}
	}

	//Logger("data is ${data}")
	def toDelete = data.findAll { !inuse.contains(it) }
	//Logger("toDelete is ${toDelete}")

	toDelete?.each { item ->
		cleanState(item.toString())
	}
}

def cleanState(id) {
LogTrace("cleanState: ${id}")
	stateRemove("Wtoday${id}")
	stateRemove("WhumTblYest${id}")
	stateRemove("WdewTblYest${id}")
	stateRemove("WtempTblYest${id}")
	stateRemove("WhumTbl${id}")
	stateRemove("WdewTbl${id}")
	stateRemove("WtempTbl${id}")

	stateRemove("today${id}")
	stateRemove("thermStor${id}")
	stateRemove("tempTblYest${id}")
	stateRemove("tempTbl${id}")
	stateRemove("oprStTblYest${id}")
	stateRemove("oprStTbl${id}")
	stateRemove("humTblYest${id}")
	stateRemove("humTbl${id}")
	stateRemove("hspTblYest${id}")
	stateRemove("hspTbl${id}")
	stateRemove("cspTblYest${id}")
	stateRemove("cspTbl${id}")
	stateRemove("fanTblYest${id}")
	stateRemove("fanTbl${id}")
}

def sectionTitleStr(title)	{ return "<h3>$title</h3>" }
def inputTitleStr(title)	{ return "<u>$title</u>" }
def pageTitleStr(title)	 { return "<h1>$title</h1>" }
def paraTitleStr(title)	 { return "<b>$title</b>" }

def imgTitle(imgSrc, titleStr, color=null, imgWidth=30, imgHeight=null) {
	def imgStyle = ""
	imgStyle += imgWidth ? "width: ${imgWidth}px !important;" : ""
	imgStyle += imgHeight ? "${imgWidth ? " " : ""}height: ${imgHeight}px !important;" : ""
	if(color) { return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""" }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""" }
}

def icons(name, napp="App") {
	def icon_names = [
		"i_dt": "delay_time",
		"i_not": "notification",
		"i_calf": "cal_filter",
		"i_set": "settings",
		"i_sw": "switch_on",
		"i_mod": "mode",
		"i_hmod": "hvac_mode",
		"i_inst": "instruct",
		"i_err": "error",
		"i_cfg": "configure",
		"i_t": "temperature"

//ERS

	]
	//return icon_names[name]
	def t0 = icon_names?."${name}"
	//LogAction("t0 ${t0}", "warn", true)
	if(t0) return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${t0}_icon.png"
	else return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${name}"
}

def gitRepo()		{ return "tonesto7/nest-manager"}
def gitBranch()		{ return "master" }
def gitPath()		{ return "${gitRepo()}/${gitBranch()}"}

def getAppImg(imgName, on = null) {
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/App/$imgName" : ""
	return (!disAppIcons || on) ? icons(imgName) : ""
}

def getDevImg(imgName, on = null) {
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/Devices/$imgName" : ""
	return (!disAppIcons || on) ? icons(imgName, "Devices") : ""
}

def logsOff() {
	Logger("debug logging disabled...")
	settingUpdate("showDebug", "false", "bool")
	settingUpdate("advAppDebug", "false", "bool")
}

def getSettingsData() {
	def sets = []
	settings?.sort().each { st ->
		sets << st
	}
	return sets
}

def getSettingVal(var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

def getStateVal(var) {
	return state[var] ?: null
}

void settingUpdate(name, value, type=null) {
	//LogTrace("settingUpdate($name, $value, $type)...")
	if(name) {
		if(value == "" || value == null || value == []) {
			settingRemove(name)
			return
		}
	}
	if(name && type) { app?.updateSetting("$name", [type: "$type", value: value]) }
	else if (name && type == null) { app?.updateSetting(name.toString(), value) }
}

void settingRemove(name) {
	//LogTrace("settingRemove($name)...")
	if(name) { app?.clearSetting(name.toString()) }
}

def stateUpdate(key, value) {
	if(key) { state?."${key}" = value; return true }
	//else { LogAction("stateUpdate: null key $key $value", "error", true); return false }
}

def stateRemove(key) {
	state.remove(key?.toString())
	return true
}

String getAutomationType() {
	return state?.autoTyp ?: null
}

String getAutoType() { return !parent ? "" : state?.autoTyp }

def getIsAutomationDisabled() {
	def dis = state?.autoDisabled
	return (dis != null && dis == true) ? true : false
}

def getTstatTiles() {
	//log.debug "${params} ${request.requestSource}"
	return renderDeviceTiles("Nest Thermostat")
}

def getTile() {
	LogTrace ("getTile()")
	//log.debug "${params} ${request.requestSource}"
	def responseMsg = ""

	def dni = "${params?.dni}"
	if (dni) {
		def device = parent.getDevice(dni)
		if (device) {
			return renderDeviceTiles(null, device)
		} else {
			responseMsg = "Device '${dni}' Not Found"
		}
	} else {
		responseMsg = "Invalid Parameters"
	}
	render contentType: "text/html",
		data: "${responseMsg}"
}

def getWeatherTile() {
	//log.debug "${params} ${request.requestSource}"
	return renderDeviceTiles("ApiXU Weather Driver Min")
}

def getProtTiles() {
	//log.debug "${params} ${request.requestSource}"
	return renderDeviceTiles("Nest Protect")
}

def renderDeviceTiles(type=null, theDev=null) {
	//log.debug "${params} ${request.requestSource}"
//	try {
		def devHtml = ""
		def navHtml = ""
		def scrStr = ""
		def allDevices = []
		if(theDev) {
			allDevices << theDev
		} else {
			allDevices = parent.getDevices() // app.getChildDevices(true)
			def weather = parent.getSettingVal("weatherDevice")
			if(weather) {
				allDevices << weather
			}
		}


		def devices = allDevices
		def devNum = 1
		def myType = type ?: "All Devices"
		devices?.sort {it?.getLabel()}.each { dev ->
			def navMap = [:]
			def hasHtml = true // (dev?.hasHtml() == true)
//Logger("renderDeviceTiles: ${dev.id} ${dev.name} ${theDev?.name} ${dev.typeName}")
			if( (dev?.typeName in ["Nest Thermostat", "Nest Protect", "ApiXU Weather Driver Min"]) &&
				( (hasHtml && !type) || (hasHtml && type && dev?.typeName == type)) ) {
LogTrace("renderDeviceTiles: ${dev.id} ${dev.name} ${theDev?.name} ${dev.typeName}")

				def myTile
				switch (dev.typeName) {
					case "Nest Thermostat":
						myTile = getTDeviceTile(devNum, dev)
						break
					case "ApiXU Weather Driver Min":
						myTile = getWDeviceTile(devNum, dev)
						break
					case "Nest Protect":
						myTile = getProtDeviceTile(devNum, dev)
						break
					default:
						LogAction("renderDeviceTiles Invalid Option Received ${dev.typeName}", "warn", true)
						break
				}

				navMap = ["key":dev?.getLabel(), "items":[]]
				def navItems = navHtmlBuilder(navMap, devNum)
				if(navItems?.html) { navHtml += navItems?.html }
				if(navItems?.js) { scrStr += navItems?.js }

				devHtml += """
				<div class="panel panel-primary" style="max-width: 600px; margin: 30 auto; position: relative;">
					<div id="key-item${devNum}" class="panel-heading">
						<h1 class="panel-title panel-title-text">${dev?.getLabel()}</h1>
					</div>
					<div class="panel-body">
						<div style="margin: auto; position: relative;">
							<div>${myTile}</div>
						</div>
					</div>
				</div>
				"""
				devNum = devNum+1
			}
		}

		def myTitle = "All Devices"
		myTitle = type ? "${type}s" : myTitle
		myTitle = theDev ? "${theDev.typeName}" : myTitle
		def html = """
		<html lang="en">
			<head>
				${getWebHeaderHtml(myType, true, true, true, true)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages_new.css">
				<style>
					h1, h2, h3, h4, h5, h6 {
						padding: 20px;
						margin: 4px;
					}
				</style>
			</head>
			<body>
				<button onclick="topFunction()" id="scrollTopBtn" title="Go to top"><i class="fa fa-arrow-up centerText" aria-hidden="true"></i></button>
				<nav id="menu-page" class="pushy pushy-left" data-focus="#nav-key-item1">
					<div class="nav-home-btn centerText"><button id="goHomeBtn" class="btn-link" title="Go Back to Home Page"><i class="fa fa-home centerText" aria-hidden="true"></i> Go Home</button></div>
					<!--Include your navigation here-->
					${navHtml}
				</nav>
				<!-- Site Overlay -->
				<div class="site-overlay"></div>

				<!-- Your Content -->
				<div id="container">
					<div id="top-hdr" class="navbar navbar-default navbar-fixed-top">
						<div class="centerText">
							<div class="row">
								<div class="col-xs-2">
									<div class="left-head-col pull-left">
										<div class="menu-btn-div">
											<div class="hamburger-wrap">
												<button id="menu-button" class="menu-btn hamburger hamburger--collapse hamburger--accessible" title="Menu" type="button">
													<span class="hamburger-box">
														<span class="hamburger-inner"></span>
													</span>
													<!--<span class="hamburger-label">Menu</span>-->
												</button>
											</div>
										</div>
									</div>
								</div>
								<div class="col-xs-8 centerText">
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> ${myTitle}</img></h3>
								</div>
								<div class="col-xs-2 right-head-col pull-right">
									<button id="rfrshBtn" type="button" class="btn refresh-btn pull-right" title="Refresh Page Content"><i id="rfrshBtnIcn" class="fa fa-refresh" aria-hidden="true"></i></button>
								</div>
							</div>
						</div>
					</div>
					<!-- Page Content -->
					<div id="page-content-wrapper">
						<div class="container">
							<div id="main" class="panel-body">
								${devHtml}
							</div>
						</div>
					</div>
				</div>
				<script>
					\$("body").flowtype({
						minFont: 7,
						maxFont: 10,
						fontRatio: 30
					});
				</script>
				<script src="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/js/diagpages.min.js"></script>
				<script>
					\$(document).ready(function() {
						${scrStr}
					});
					\$("#goHomeBtn").click(function() {
						closeNavMenu();
						toggleMenuBtn();
						window.location.replace('${request?.requestSource == "local" ? getLocalEndpointUrl("deviceTiles") : getAppEndpointUrl("deviceTiles")}');
					});
				</script>
			</body>
		</html>
		"""
/* """ */
		render contentType: "text/html", data: html
//	} catch (ex) { log.error "renderDeviceData Exception:", ex }
}

def navHtmlBuilder(navMap, idNum) {
	def res = [:]
	def htmlStr = ""
	def jsStr = ""
	if(navMap?.key) {
		htmlStr += """
			<div class="nav-cont-bord-div nav-menu">
			  <div class="nav-cont-div">
				<li class="nav-key-item"><a id="nav-key-item${idNum}">${navMap?.key}<span class="icon"></span></a></li>"""
		jsStr += navJsBuilder("nav-key-item${idNum}", "key-item${idNum}")
	}
	if(navMap?.items) {
		def nItems = navMap?.items
		nItems?.each {
			htmlStr += """\n<li class="nav-subkey-item"><a id="nav-subitem${idNum}-${it?.toString().toLowerCase()}">${it}<span class="icon"></span></a></li>"""
			jsStr += navJsBuilder("nav-subitem${idNum}-${it?.toString().toLowerCase()}", "item${idNum}-${it?.toString().toLowerCase()}")
		}
	}
	htmlStr += """\n		</div>
						</div>"""
	res["html"] = htmlStr
	res["js"] = jsStr
	return res
}

def navJsBuilder(btnId, divId) {
	def res = """
			\$("#${btnId}").click(function() {
				\$("html, body").animate({scrollTop: \$("#${divId}").offset().top - hdrHeight - 20},500);
				closeNavMenu();
				toggleMenuBtn();
			});
	"""
	return "\n${res}"
}



def getTodaysUsage(dev) {
	def hm = getHistoryStore(dev)
	def timeMap = [:]
	timeMap << ["cooling":["tData":secToTimeMap(hm?."OpSt_Day${hm.cDy}_c"), "tSec":hm?."OpSt_Day${hm.cDy}_c"]]
	timeMap << ["heating":["tData":secToTimeMap(hm?."OpSt_Day${hm.cDy}_h"), "tSec":hm?."OpSt_Day${hm.cDy}_h"]]
	timeMap << ["idle":["tData":secToTimeMap(hm?."OpSt_Day${hm.cDy}_i"), "tSec":hm?."OpSt_Day${hm.cDy}_i"]]
	timeMap << ["fanonly":["tData":secToTimeMap(hm?."OpSt_Day${hm.cDy}_fo"), "tSec":hm?."OpSt_Day${hm.cDy}_fo"]]
	timeMap << ["fanOn":["tData":secToTimeMap(hm?."FanM_Day${hm.cDy}_On"), "tSec":hm?."FanM_Day${hm.cDy}_On"]]
	timeMap << ["fanAuto":["tData":secToTimeMap(hm?."FanM_Day${hm.cDy}_auto"), "tSec":hm?."FanM_Day${hm.cDy}_auto"]]
	return timeMap
}

def getWeeksUsage(dev) {
	def hm = getHistoryStore(dev)
	def timeMap = [:]
	def coolVal = 0L
	def heatVal = 0L
	def idleVal = 0L
	def fanonlyVal = 0L
	def fanOnVal = 0L
	def fanAutoVal = 0L
	for(int i = 1; i <= 7; i++) {
		coolVal = coolVal + hm?."OpSt_Day${i}_c"?.toInteger()
		heatVal = heatVal + hm?."OpSt_Day${i}_h"?.toInteger()
		idleVal = idleVal + hm?."OpSt_Day${i}_i"?.toInteger()
		fanonlyVal = fanonlyVal + hm?."OpSt_Day${i}_fo"?.toInteger()
		fanOnVal = fanOnVal + hm?."FanM_Day${i}_On"?.toInteger()
		fanAutoVal = fanAutoVal + hm?."FanM_Day${i}_auto"?.toInteger()
	}
	timeMap << ["cooling":["tData":secToTimeMap(coolVal), "tSec":coolVal]]
	timeMap << ["heating":["tData":secToTimeMap(heatVal), "tSec":heatVal]]
	timeMap << ["idle":["tData":secToTimeMap(idleVal), "tSec":idleVal]]
	timeMap << ["fanonly":["tData":secToTimeMap(fanonlyVal), "tSec":fanonlyVal]]
	timeMap << ["fanOn":["tData":secToTimeMap(fanOnVal), "tSec":fanOnVal]]
	timeMap << ["fanAuto":["tData":secToTimeMap(fanAutoVal), "tSec":fanAutoVal]]
	//log.debug "weeksUsage: ${timeMap}"
	return timeMap
}

def getMonthsUsage(monNum,dev) {
	LogTrace("getMonthsUsage ${monNum}")
	def hm = getHistoryStore(dev)
	def timeMap = [:]
	def mVal = (monNum >= 1 && monNum <= 12) ? monNum : hm?.curMon
	timeMap << ["cooling":["tData":secToTimeMap(hm?."OpSt_M${mVal}_c"), "tSec":hm?."OpSt_M${mVal}_c"]]
	timeMap << ["heating":["tData":secToTimeMap(hm?."OpSt_M${mVal}_h"), "tSec":hm?."OpSt_M${mVal}_h"]]
	timeMap << ["idle":["tData":secToTimeMap(hm?."OpSt_M${mVal}_i"), "tSec":hm?."OpSt_M${mVal}_i"]]
	timeMap << ["fanonly":["tData":secToTimeMap(hm?."OpSt_M${mVal}_fo"), "tSec":hm?."OpSt_M${mVal}_fo"]]
	timeMap << ["fanOn":["tData":secToTimeMap(hm?."FanM_M${mVal}_On"), "tSec":hm?."FanM_M${mVal}_On"]]
	timeMap << ["fanAuto":["tData":secToTimeMap(hm?."FanM_M${mVal}_auto"), "tSec":hm?."FanM_M${mVal}_auto"]]
	//log.debug "monthsUsage: $mVal ${timeMap}"
	return timeMap
}

def getLast3MonthsUsageMap(dev) {
	def hm = getHistoryStore(dev)
	def timeMap = [:]
	def cnt = 1
	def mVal = (int) hm?.curMon
	if(mVal) {
		for(int i=1; i<=3; i++) {
			def newMap = [:]
			def mName = getMonthNumToStr(mVal)
			//log.debug "$mName Usage - Idle: (${hm?."OpSt_M${mVal}_i"}) | Heat: (${hm?."OpSt_M${mVal}_h"}) | Cool: (${hm?."OpSt_M${mVal}_c"})"
			newMap << ["cooling":["tSec":(hm?."OpSt_M${mVal}_c" ?: 0L), "iNum":cnt, "mName":mName]]
			newMap << ["heating":["tSec":(hm?."OpSt_M${mVal}_h" ?: 0L), "iNum":cnt, "mName":mName]]
			newMap << ["idle":["tSec":(hm?."OpSt_M${mVal}_i" ?: 0L), "iNum":cnt, "mName":mName]]
			newMap << ["fanonly":["tSec":(hm?."OpSt_M${mVal}_fo" ?: 0L), "iNum":cnt, "mName":mName]]
			newMap << ["fanOn":["tSec":(hm?."FanM_M${mVal}_On" ?: 0L), "iNum":cnt, "mName":mName]]
			newMap << ["fanAuto":["tSec":(hm?."FanM_M${mVal}_auto" ?: 0L), "iNum":cnt, "mName":mName]]
			timeMap << [(mVal):newMap]
			mVal = ((mVal==1) ? 12 : mVal-1)
			cnt = cnt+1
		}
	}
	return timeMap
}

def getMonthNumToStr(val) {
	def mons = [1:"Jan", 2:"Feb", 3:"Mar", 4:"Apr", 5:"May", 6:"June", 7:"July", 8:"Aug", 9:"Sept", 10:"Oct", 11:"Nov", 12:"Dec"]
	def res = mons?.find { key, value -> key.toInteger() == val?.toInteger() }
	return res ? res?.value : "unknown"
}

/*
def getYearsUsage(dev) {
	def hm = getHistoryStore(dev)
	def timeMap = [:]
	def coolVal = 0L
	def heatVal = 0L
	def idleVal = 0L
	def fanonlyVal = 0L
	def fanOnVal = 0L
	def fanAutoVal = 0L
	for(int i = 1; i <= 12; i++) {
		coolVal = coolVal + hm?."OpSt_M${i}_c"?.toInteger()
		heatVal = heatVal + hm?."OpSt_M${i}_h"?.toInteger()
		idleVal = idleVal + hm?."OpSt_M${i}_i"?.toInteger()
		fanonlyVal = fanonlyVal + hm?."OpSt_M${i}_fo"?.toInteger()
		fanOnVal = fanOnVal + hm?."FanM_M${i}_On"?.toInteger()
		fanAutoVal = fanAutoVal + hm?."FanM_M${i}_auto"?.toInteger()
	}
	timeMap << ["cooling":["tData":secToTimeMap(coolVal), "tSec":coolVal]]
	timeMap << ["heating":["tData":secToTimeMap(heatVal), "tSec":heatVal]]
	timeMap << ["idle":["tData":secToTimeMap(idleVal), "tSec":idleVal]]
	timeMap << ["fanonly":["tData":secToTimeMap(fanonlyVal), "tSec":fanonlyVal]]
	timeMap << ["fanOn":["tData":secToTimeMap(fanOnVal), "tSec":fanOnVal]]
	timeMap << ["fanAuto":["tData":secToTimeMap(fanAutoVal), "tSec":fanAutoVal]]
	//log.debug "yearsUsage: ${timeMap}"
	return timeMap
}

def doSomething() {
	getNestMgrReport()
	//getTodaysUsage()
	//getWeeksUsage()
	//getMonthsUsage()
	//getYearsUsage()
}
*/

Map getHistoryStore(dev) {
	LogTrace("getHistoryStore(${dev.id})...")
	Map thm = state?."thermStor${dev.id}"
	if(thm == null || thm == [:]) {
		log.error "thm is null"
		return
	}
//Logger("getHistoryStore:	thm: ${thm}")
	Map hm = thm.clone()

	long Op_cusage = getSumUsage(state."oprStTbl${dev.id}", "cooling")
	long Op_husage = getSumUsage(state."oprStTbl${dev.id}", "heating")
	long OpIdle = getSumUsage(state."oprStTbl${dev.id}", "idle")
	long Op_fo = getSumUsage(state."oprStTbl${dev.id}", "fan only")
	long FanOn = getSumUsage(state."fanTbl${dev.id}", "on")
	long FanAuto = getSumUsage(state."fanTbl${dev.id}", "auto")

	//log.info "FanOn ${FanOn} FanAuto: ${FanAuto} OpIdle: ${OpIdle} cool: ${Op_cusage} heat: ${Op_husage}"
	//log.debug "cDy ${hm.cDy} | curMon ${hm.curMon} | curYr: ${hm.curYr}"

	hm."OpSt_Day${hm.cDy}_c" = Op_cusage
	hm."OpSt_Day${hm.cDy}_h" = Op_husage
	hm."OpSt_Day${hm.cDy}_i" = OpIdle
	hm."OpSt_Day${hm.cDy}_fo" = Op_fo
	hm."FanM_Day${hm.cDy}_On" = FanOn
	hm."FanM_Day${hm.cDy}_auto" = FanAuto

	long t1 = hm?."OpSt_M${hm.curMon}_c"?.toInteger() ?: 0L
	hm."OpSt_M${hm.curMon}_c" = t1 + Op_cusage
	t1 = hm?."OpSt_M${hm.curMon}_h"?.toInteger() ?: 0L
	hm."OpSt_M${hm.curMon}_h" = t1 + Op_husage
	t1 = hm?."OpSt_M${hm.curMon}_i"?.toInteger() ?: 0L
	hm."OpSt_M${hm.curMon}_i" = t1 + OpIdle
	t1 = hm?."OpSt_M${hm.curMon}_fo"?.toInteger() ?: 0L
	hm."OpSt_M${hm.curMon}_fo" = t1 + Op_fo
	t1 = hm?."FanM_M${hm.curMon}_On"?.toInteger() ?: 0L
	hm."FanM_M${hm.curMon}_On" = t1 + FanOn
	t1 = hm?."FanM_M${hm.curMon}_auto"?.toInteger() ?: 0L
	hm."FanM_M${hm.curMon}_auto" = t1 + FanAuto

	t1 = hm?.OpSt_thisY_c?.toInteger() ?: 0L
	hm.OpSt_thisY_c = t1 + Op_cusage
	t1 = hm?.OpSt_thisY_h?.toInteger() ?: 0L
	hm.OpSt_thisY_h = t1 + Op_husage
	t1 = hm?.OpSt_thisY_i?.toInteger() ?: 0L
	hm.OpSt_thisY_i = t1 + OpIdle
	t1 = hm?.OpSt_thisY_fo?.toInteger() ?: 0L
	hm.OpSt_thisY_fo = t1 + Op_fo
	t1 = hm?.FanM_thisY_On?.toInteger() ?: 0L
	hm.FanM_thisY_On = t1 + FanOn
	t1 = hm?.FanM_thisY_auto?.toInteger() ?: 0L
	hm.FanM_thisY_auto = t1 + FanAuto

	return hm
}

def getIntListAvg(itemList) {
	//log.debug "itemList: ${itemList}"
	def avgRes = 0
	def iCnt = itemList?.size()
	if(iCnt >= 1) {
		if(iCnt > 1) {
			avgRes = (itemList?.sum().toDouble() / iCnt.toDouble()).round(0)
		} else { itemList?.each { avgRes = avgRes + it.toInteger() } }
	}
	//log.debug "[getIntListAvg] avgRes: $avgRes"
	return avgRes.toInteger()
}

def secToTimeMap(long seconds) {
	long sec = (seconds % 60) ?: 0L
	long minutes = ((seconds % 3600) / 60) ?: 0L
	long hours = ((seconds % 86400) / 3600) ?: 0L
	long days = (seconds / 86400) ?: 0L
	long years = (days / 365) ?: 0L
	def res = ["m":minutes, "h":hours, "d":days, "y":years]
	return res
}

def extWeatTempAvail() {
	//def weather = parent.getSettingVal("weatherDevice")
	if(state?.haveWeather == null) {
		state.haveWeather = parent.getSettingVal("weatherDevice") ? true : false
	}
	return state?.haveWeather ? true : false
}

def getTDeviceTile(devNum, dev) {
	LogTrace("getTDeviceTile ${dev.label} ${dev.id}")
//	try {
		def tempStr = getTempUnitStr()
		//LogAction("State Size: ${getStateSize()} (${getStateSizePerc()}%)")
//Logger("T1")
		def canHeat = getCanHeat(dev)
		def canCool = getCanCool(dev)
		def hasFan = getHasFan(dev)
		def leafImg = getHasLeaf(dev) ? getDevImg("nest_leaf_on.gif") : getDevImg("nest_leaf_off.gif")
//Logger("T2")

		def timeToTarget = dev.currentState("timeToTarget").value
//Logger("3")
		def sunCorrectStr = dev.currentState("sunlightCorrectionEnabled").value.toBoolean() ? "Enabled (${dev.currentState("sunlightCorrectionActive").value.toBoolean() == true ? "Active" : "Inactive"})" : "Disabled"
		def refreshBtnHtml = true /* parent.getStateVal("mobileClientType") == "ios" */ ?
				"""<div class="pageFooterBtn"><button type="button" class="btn btn-info pageFooterBtn" onclick="reloadTstatPage()"><span>&#10227;</span> Refresh</button></div>""" : ""
//Logger("4")
		def chartHtml = (
//				state?.showGraphs &&
				state?."tempTbl${dev.id}"?.size() > 0 &&
				state?."oprStTbl${dev.id}"?.size() > 0 &&
				state?."tempTblYest${dev.id}"?.size() > 0 &&
				state?."humTbl${dev.id}"?.size() > 0 &&
				state?."cspTbl${dev.id}"?.size() > 0 &&
				state?."hspTbl${dev.id}"?.size() > 0) ? showChartHtml(devNum, dev) : (true /* state?.showGraphs */ ? hideChartHtml() : "")

		def schedData = state?.curAutoSchedData
		def schedHtml = ""
//Logger("5")
		if(schedData) {
			schedHtml = """
				<section class="sectionBgTile">
					<h3>Automation Schedule</h3>
					<table class="sched">
						<col width="90%">
						<thead class="devInfoTile">
							<th>Active Schedule</th>
						</thead>
						<tbody>
							<tr><td>#${schedData?.scdNum} - ${schedData?.schedName}</td></tr>
						</tbody>
					</table>
					<h3>Zone Status</h3>

					<table class="sched">
						<col width="50%">
						<col width="50%">
						<thead class="devInfoTile">
							<th>Temp Source:</th>
							<th>Zone Temp:</th>
						</thead>
						<tbody class="sched">
							<tr>
								<td>${schedData?.tempSrcDesc}</td>
								<td>${schedData?.curZoneTemp}&deg;${state?.tempUnit}</td>
							</tr>
						</tbody>
					</table>
					<table class="sched">
						<col width="45%">
						<col width="45%">
						<thead class="devInfoTile">
							<th>Desired Heat Temp</th>
							<th>Desired Cool Temp</th>
						</thead>
						<tbody>
							<tr>
								<td>${schedData?.reqSenHeatSetPoint ? "${schedData?.reqSenHeatSetPoint}&deg;${state?.tempUnit}": "Not Available"}</td>
								<td>${schedData?.reqSenCoolSetPoint ? "${schedData?.reqSenCoolSetPoint}&deg;${state?.tempUnit}": "Not Available"}</td>
							</tr>
						</tbody>
					</table>
				</section>
				<br>
			"""
		}

		def chgDescHtml = ""
//Logger("6")
		def onlineStatus = dev.currentState("onlineStatus").value
		def apiStatus = dev.currentState("apiStatus").value

		def html = """
			<div class="device">
				<div class="swiper-container-${devNum}" style="max-width: 100%; overflow: hidden;">
					<!-- Additional required wrapper -->
					<div class="swiper-wrapper">
						<!-- Slides -->
						<div class="swiper-slide">
							${schedHtml == "" ? "" : "${schedHtml}"}
							<section class="sectionBgTile">
								<h3>Device Info</h3>
								<table class="devInfoTile">
								  <col width="50%">
								  <col width="50%">
								  <thead>
									<th>Time to Target</th>
									<th>Sun Correction</th>
								  </thead>
								  <tbody>
									<tr>
									  <td>${timeToTarget}</td>
									  <td>${sunCorrectStr}</td>
									</tr>
								  </tbody>
								</table>
								<table class="devInfoTile">
								<col width="40%">
								<col width="20%">
								<col width="40%">
								<thead>
								  <th>Network Status</th>
								  <th>Leaf</th>
								  <th>API Status</th>
								</thead>
								<tbody>
								  <tr>
									<td${onlineStatus != "online" ? """ class="redText" """ : ""}>${onlineStatus.toString().capitalize()}</td>
									<td><img src="${leafImg}" class="leafImg"></img></td>
								  	<td${apiStatus != "Good" ? """ class="orangeText" """ : ""}>${apiStatus}</td>
								  </tr>
								</tbody>
							  </table>
							  <table class="devInfoTile">
								<col width="50%">
								<col width="50%">
								  <thead>
									<th>Firmware Version</th>
								  	<th>Nest Checked-In</th>
			<!--						<th>Debug</th>		-->
			<!--						<th>Device Type</th>	-->
								  </thead>
								<tbody>
								  <tr>
									<td>${dev.currentState("softwareVer").value?.toString()}</td>
									<td class="dateTimeTextTile">${dev.currentState("lastConnection").value?.toString()}</td>
			<!--						<td>${state?.debugStatus}</td>		-->
			<!--						<td>${state?.devTypeVer?.toString()}</td>	-->
								  </tr>
								</tbody>
							  </table>
			<!--				  <table class="devInfoTile">	-->
			<!--					<thead>	-->
			<!--					  <th>Nest Checked-In</th>	-->
			<!--					</thead>	-->
			<!--					<tbody>	-->
			<!--					  <tr>	-->
			<!--						<td class="dateTimeTextTile">${dev.currentState("lastConnection").value?.toString()}</td>	-->
			<!--					  </tr>	-->
			<!--					</tbody>	-->
			<!--				  </table>	-->
							</section>
							${schedHtml == "" ? """<br>${chgDescHtml}""" : ""}
						</div>
						${schedHtml == "" ? "" : """${chgDescHtml}"""}
						${chartHtml}
					</div>
					<!-- If we need pagination -->
					<div style="text-align: center; padding: 20px;">
						<p class="slideFooterTextTile">Swipe/Drag to Change Slide</p>
					</div>
					<div class="swiper-pagination"></div>
				</div>
			</div>
			<script>
				var mySwiper${devNum} = new Swiper ('.swiper-container-${devNum}', {
					direction: 'horizontal',
					initialSlide: 0,
					lazyLoading: true,
					loop: false,
					slidesPerView: '1',
					centeredSlides: true,
					spaceBetween: 100,
					autoHeight: true,
					keyboardControl: true,
					mousewheelControl: true,
					iOSEdgeSwipeDetection: true,
					iOSEdgeSwipeThreshold: 20,
					parallax: true,
					slideToClickedSlide: true,

					effect: 'coverflow',
					coverflow: {
						rotate: 50,
						stretch: 0,
						depth: 100,
						modifier: 1,
						slideShadows : true
					},
					onTap: function(s, e) {
						s.slideNext(false);
						if(s.clickedIndex >= s.slides.length) {
							s.slideTo(0, 400, false)
						}
					},
					pagination: '.swiper-pagination',
					paginationHide: false,
					paginationClickable: true
				});
				function reloadTstatPage() {
					window.location.reload();
				}
			</script>
		"""
/* """ */
//		render contentType: "text/html", data: html, status: 200
/*
	} catch (ex) {
		log.error "getTDeviceTile Exception:", ex
		//exceptionDataHandler(ex?.message, "getTDeviceTile")
	}
*/
}

def getWebHeaderHtml(title, clipboard=true, vex=false, swiper=false, charts=false) {
	def html = """
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
		<meta name="description" content="NST Graphs">
		<meta name="author" content="Anthony S.">
		<meta http-equiv="cleartype" content="on">
		<meta name="MobileOptimized" content="320">
		<meta name="HandheldFriendly" content="True">
		<meta name="apple-mobile-web-app-capable" content="yes">

		<title>NST Graphs ('${parent.getStateVal("structureName")}') - ${title}</title>

		<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
		<link href="https://fonts.googleapis.com/css?family=Roboto" rel="stylesheet">
		<script src="https://use.fontawesome.com/fbe6a4efc7.js"></script>
		<script src="https://fastcdn.org/FlowType.JS/1.1/flowtype.js"></script>
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/normalize/7.0.0/normalize.min.css">
		<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css" integrity="sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u" crossorigin="anonymous">
		<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css" integrity="sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp" crossorigin="anonymous">
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/hamburgers/0.9.1/hamburgers.min.css">
		<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js" integrity="sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa" crossorigin="anonymous"></script>
		<script type="text/javascript">
			const serverUrl = '${request?.requestSource == "local" ? getLocalApiServerUrl() : apiServerUrl()}';
			const cmdUrl = '${request?.requestSource == "local" ? getLocalEndpointUrl('deviceTiles') : getAppEndpointUrl('deviceTiles')}';
		</script>
	"""
	html += clipboard ? """<script src="https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/1.7.1/clipboard.min.js"></script>""" : ""
	html += vex ? """<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/js/vex.combined.min.js"></script>""" : ""
	html += swiper ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Swiper/4.3.3/css/swiper.min.css" />""" : ""
	html += vex ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/css/vex.min.css" />""" : ""
	html += vex ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/css/vex-theme-top.min.css" />""" : ""
	html += swiper ? """<script src="https://cdnjs.cloudflare.com/ajax/libs/Swiper/4.3.3/js/swiper.min.js"></script>""" : ""
	html += charts ? """<script src="https://www.gstatic.com/charts/loader.js"></script>""" : ""
	html += vex ? """<script>vex.defaultOptions.className = 'vex-theme-default'</script>""" : ""

	return html
}

def hideChartHtml() {
	def data = """
		<div class="swiper-slide">
			<section class="sectionBg" style="min-height: 250px;">
				<h3>Event History</h3>
			<br>
			<div class="centerText">
				<p>Waiting for more data to be collected...</p>
				<p>This may take a few hours</p>
			</div>
			</section>
		</div>
	"""
	return data
}

String getDataTString(Integer seriesIndex,dev) {
	//LogAction("getDataTString ${seriesIndex}", "trace")
	def dataTable = []
	switch (seriesIndex) {
		case 1:
			dataTable = state?."tempTblYest${dev.id}"
			break
		case 2:
			dataTable = state?."tempTbl${dev.id}"
			break
		case 3:
			dataTable = state?."oprStTbl${dev.id}"
			break
		case 4:
			dataTable = state?."humTbl${dev.id}"
			break
		case 5:
			dataTable = state?."cspTbl${dev.id}"
			break
		case 6:
			dataTable = state?."hspTbl${dev.id}"
			break
		case 7:
			dataTable = state?."eTempTbl"
			break
		case 8:
			dataTable = state?."fanTbl${dev.id}"
			break
	}

	def lastVal = 200

	//LogAction("getDataTString ${seriesIndex} ${dataTable}")
	//LogAction("getDataTString ${seriesIndex}")

	def lastAdded = false
	def dataArray
	def myval
	def myindex
	def lastdataArray = null
	def dataString = ""

	if(seriesIndex == 5) {
	// state.can_cool
	}
	if(seriesIndex == 6) {
		// state.can_heat
	}
	if(seriesIndex == 8) {
		//state?.has_fan
	}
	def myhas_fan = getHasFan(dev) && false ? true : false	// false because not graphing fan operation now

	def has_weather = extWeatTempAvail()
	//if( !(state?.curWeatData == null || state?.curWeatData == [:])) { has_weather = true }
	def canHeat = getCanHeat(dev)
	def canCool = getCanCool(dev)

	def datacolumns

	myindex = seriesIndex
//ERSERS
	datacolumns = 8
	//if(state?.can_heat && state?.can_cool && myhas_fan && has_weather) { datacolumns = 8 }
	if(!myhas_fan) {
		datacolumns -= 1
	}
	if(!has_weather) {
		datacolumns -= 1
		if(myindex == 8) { myindex = 7 }
	}
	if((!canHeat && canCool) || (canHeat && !canCool)) {
		datacolumns -= 1
		if(myindex >= 6) { myindex -= 1 }
	}
	switch (datacolumns) {
		case 8:
			dataArray = [[0,0,0],null,null,null,null,null,null,null,null]
			break
		case 7:
			dataArray = [[0,0,0],null,null,null,null,null,null,null]
			break
		case 6:
			dataArray = [[0,0,0],null,null,null,null,null,null]
			break
		case 5:
			dataArray = [[0,0,0],null,null,null,null,null]
			break
		default:
			LogAction("getDataTString: bad column result", "error")
	}

	dataTable.any { it ->
		myval = it[2]

		//convert idle / non-idle to numeric value
		if(myindex == 3) {
			switch(myval) {
				case "idle":
					myval = 0
				break
				case "cooling":
					myval = 8
				break
				case "heating":
					myval = 16
				break
				case "fan only":
					myval = 4
				break
				default:
					myval = 0
				break
			}
		}
/*
		if(myhas_fan && seriesIndex == 8) {
			switch(myval) {
				case "auto":
					myval = 0
					break
				case "on":
					myval = 8
					break
				case "circulate":
					myval = 8
					break
				default:
					myval = 0
					break

			}
		}
*/

		if(seriesIndex == 5) {
			if(myval == 0) { return false }
		// state.can_cool
		}
		if(seriesIndex == 6) {
			if(myval == 0) { return false }
		// state.can_heat
		}

		dataArray[myindex] = myval
		dataArray[0] = [it[0],it[1],0]

		dataString += dataArray?.toString() + ","
		return false
	}

	if(dataString == "") {
		dataArray[0] = [0,0,0]
		//dataArray[myindex] = 0
		dataString += dataArray?.toString() + ","
	}

	//LogAction("getDataTString ${seriesIndex} datacolumns: ${datacolumns} myindex: ${myindex} datastring: ${dataString}")
	return dataString
}



/*
	 variable	 attribute for history	getRoutine			variable is present

	temperature		"temperature"		getTemp				true				  #
	coolSetpoint	  "coolingSetpoint"		getCoolTemp		state.can_cool				  #
	heatSetpoint	  "heatingSetpoint"		getHeatTemp		state.can_heat				  #
	operatingState	"thermostatOperatingState"	getHvacState			true				idle cooling heating
	operatingMode	"thermostatMode"		getHvacMode			true				heat cool off auto
	presence		"presence"			getPresence			true				present  not present
*/

void getSomeData(dev, devpoll = false) {
	//LogTrace("getSomeData ${app} ${dev.label} ${dev.id}")


//ERS
	def today = new Date()
	def todayDay = today.format("dd",location.timeZone)
//Logger("getSomeData: ${today} ${todayDay} ${dev.id}")

	if(state?."tempTbl${dev.id}" == null) {

		state."tempTbl${dev.id}" = []
		state."oprStTbl${dev.id}" = []
		state."humTbl${dev.id}" = []
		state."cspTbl${dev.id}" = []
		state."hspTbl${dev.id}" = []
		state."eTempTbl" = []
		state."fanTbl${dev.id}" = []
		addNewData(dev)
	}

	def tempTbl = state?."tempTbl${dev.id}"
	def oprStTbl = state?."oprStTbl${dev.id}"
	def humTbl = state?."humTbl${dev.id}"
	def cspTbl = state?."cspTbl${dev.id}"
	def hspTbl = state?."hspTbl${dev.id}"
	def eTempTbl = state?."eTempTbl"
	def fanTbl = state?."fanTbl${dev.id}"


/*
	if(fanTbl == null) {		// upgrade cleanup ERSTODO
		state.fanTbl = []; fanTbl = state.fanTbl; state.fanTblYest = fanTbl
	}
	if(eTempTbl == null) {		// upgrade cleanup ERSTODO
		state.eTempTbl = []; eTempTbl = state.eTempTbl; state.eTempTblYest = eTempTbl
	}
*/

	def hm = state?."thermStor${dev.id}"
	if(hm == null) {
		initHistoryStore(dev)
	}

	if(state?."tempTblYest${dev.id}"?.size() == 0) {
		state."tempTblYest${dev.id}" = tempTbl
		state."oprStTblYest${dev.id}" = oprStTbl
		state."humTblYest${dev.id}" = humTbl
		state."cspTblYest${dev.id}" = cspTbl
		state."hspTblYest${dev.id}" = hspTbl
		state."eTempTblYest" = eTempTbl
		state."fanTblYest${dev.id}" = fanTbl
	}

// DAY CHANGE
	if(!state?."today${dev.id}" || state."today${dev.id}" != todayDay) {
		state."today${dev.id}" = todayDay
		state."tempTblYest${dev.id}" = tempTbl
		state."oprStTblYest${dev.id}" = oprStTbl
		state."humTblYest${dev.id}" = humTbl
		state."cspTblYest${dev.id}" = cspTbl
		state."hspTblYest${dev.id}" = hspTbl
		state."eTempTblYest" = eTempTbl
		state."fanTblYest${dev.id}" = fanTbl

		state."tempTbl${dev.id}" = []
		state."oprStTbl${dev.id}" = []
		state."humTbl${dev.id}" = []
		state."cspTbl${dev.id}" = []
		state."hspTbl${dev.id}" = []
		state."eTempTbl" = []
		state."fanTbl${dev.id}" = []
		updateOperatingHistory(today, dev)

	}
	//initHistoryStore(dev)	// ERSTODO DEBUGGING
	//updateOperatingHistory(today, dev) // ERSTODO DEBUGGING
	addNewData(dev)
	//def bb = getHistoryStore(dev)	// ERSTODO DEBUGGING
}

def getCanHeat(dev) {
	def t0 = dev.currentState("canHeat")?.value
	return t0?.toString() == "false" ? false : true
}

def getCanCool(dev) {
	def t0 = dev.currentState("canCool")?.value
	return t0?.toString() == "false" ? false : true
}

def getHasFan(dev) {
	def t0 = dev.currentState("hasFan")?.value
	return t0?.toString() == "false" ? false : true
}

def getHasLeaf(dev) {
	def t0 = dev.currentState("hasLeaf")?.value
	return !t0 ? "unknown" : t0?.toString()
}

private cast(value, dataType) {
	switch(dataType) {
		case "number":
			if (value == null) return (int) 0
			if (value instanceof String) {
				if (value.isInteger())
					return value.toInteger()
				if (value.isFloat())
					return (int) Math.floor(value.toFloat())
				if (value in trueStrings)
					return (int) 1
			}
			def result = (int) 0
			try {
				result = (int) value
			} catch(all) {
				result = (int) 0
			}
			return result ? result : (int) 0
		case "decimal":
			if (value == null) return (float) 0
			if (value instanceof String) {
				if (value.isFloat())
					return (float) value.toFloat()
				if (value.isInteger())
					return (float) value.toInteger()
				if (value in trueStrings)
					return (float) 1
			}
			def result = (float) 0
			try {
				result = (float) value
			} catch(all) {
			}
			return result ? result : (float) 0
	}
}

def getTemp(dev) {
	def t0 = dev.currentState("temperature")?.value
	int t1 = cast(t0, "decimal")
	return t1
}

def getApiXUData(dev) {
	def obs = [:]
	if(state?.obs) {
		obs = state.obs
		def t0 = "${obs.current.last_updated}"
		def t1 = formatDt(Date.parse("yyyy-MM-dd HH:mm", t0))
//	def start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
//	def localMidNight = Date.parse("yyyy-MM-dd HH:mm", t0).getTime()
//	def localMidNightf = Date.parse("yyyy-MM-dd hh:mm a", t0)
		def s = GetTimeDiffSeconds(t1, null, "getApiXUData").toInteger()
		if(s > (60*60*4)) {  // we are doing this primarily for forecasts
			stateRemove("obs")
		} else return obs
	}
	def t0 = dev.currentState("apiXUquery")?.value
	if(t0) {
		def myUri = t0
		def params = [ uri: myUri ]
		try {
			httpGet(params) { resp ->
				if (resp?.data) {
					obs << resp.data;
					state.obs = obs
					return obs
				} else log.error "http call for ApiXU weather api did not return data: $resp";
			}
		} catch (e) { log.error "http call failed for ApiXU weather api: $e" }
//		log.debug "$obs"
	}
	return obs
}

def getDewpoint(dev) {
	def t0 = dev.currentState("dewpoint")?.value
	int t1 = cast(t0, "decimal")
	return t1
}

def getCoolTemp(dev) {
	def t0 = dev.currentState("coolingSetpoint")?.value
	return t0 ? t0 : 0
}

def getHeatTemp(dev) {
	def t0 = dev.currentState("heatingSetpoint")?.value
	return t0 ? t0 : 0
}

def getHvacState(dev) {
	def t0 = dev.currentState("thermostatOperatingState")?.value
	return !t0 ? "unknown" : t0?.toString()
}

def getHumidity(dev) {
	def t0 = dev.currentState("humidity")?.value
	return t0 ? t0 : 0
}

def getFanMode(dev) {
	def t0 = dev.currentState("thermostatFanMode")?.value
	return !t0 ? "unknown" : t0?.toString()
}

def getTZ(dev) {
	def t0 = dev.currentState("tz_id")?.value
	return !t0 ? null : t0?.toString()
}

/*
def getDeviceVarAvg(items, var) {
	def tmpAvg = []
	def tempVal = 0
	if(!items) { return tempVal }
	else {
		tmpAvg = items*."${var}"
		if(tmpAvg && tmpAvg?.size() > 0) { tempVal = (tmpAvg?.sum().toDouble() / tmpAvg?.size().toDouble()).round(1) }
	}
	return tempVal.toDouble()
}

def getDeviceTempAvg(items) {
	return getDeviceVarAvg(items, "currentTemperature")
}
*/


def addNewData(dev) {
	def currentTemperature = getTemp(dev)
	def currentcoolSetPoint = getCoolTemp(dev)
	def currentheatSetPoint = getHeatTemp(dev)
	def currentoperatingState = getHvacState(dev)
	def currenthumidity = getHumidity(dev)
	def currentfanMode = getFanMode(dev)

	def temp0
	def weather = parent.getSettingVal("weatherDevice")
		if(weather) {
		temp0 = getTemp(weather)
	}
	def currentExternal = temp0

	def tempTbl = state?."tempTbl${dev.id}"
	def oprStTbl = state?."oprStTbl${dev.id}"
	def humTbl = state?."humTbl${dev.id}"
	def cspTbl = state?."cspTbl${dev.id}"
	def hspTbl = state?."hspTbl${dev.id}"
	def eTempTbl = state?."eTempTbl"
	def fanTbl = state?."fanTbl${dev.id}"

	// add latest coolSetpoint & temperature readings for the graph
	def newDate = new Date()
	def hr = newDate.format("H", location.timeZone) as Integer
	def mins = newDate.format("m", location.timeZone) as Integer

//Logger("addNewData currentTemp: ${currentTemperature}	tempTbl: ${tempTbl}", "trace")
//Logger("addNewData ${dev.id} hr: ${hr} mins: ${mins}", "trace")

	state."tempTbl${dev.id}" =	addValue(tempTbl, hr, mins, currentTemperature)
	state."oprStTbl${dev.id}" =	addValue(oprStTbl, hr, mins, currentoperatingState)
	state."humTbl${dev.id}" =	addValue(humTbl, hr, mins, currenthumidity)
	state."cspTbl${dev.id}" =	addValue(cspTbl, hr, mins, currentcoolSetPoint)
	state."hspTbl${dev.id}" =	addValue(hspTbl, hr, mins, currentheatSetPoint)
	state."eTempTbl" =		addValue(eTempTbl, hr, mins, currentExternal)
	state."fanTbl${dev.id}" =	addValue(fanTbl, hr, mins, currentfanMode)
}



def updateOperatingHistory(today, dev) {
	LogTrace("updateOperatingHistory(${today}, ${dev.id})...", "trace")

	def dayChange = false
	def monthChange = false
	def yearChange = false

	def hm = state?."thermStor${dev.id}"
	if(hm == null) {
		log.error "hm is null"
		return
	}
	def dayNum = today.format("u", location.timeZone).toInteger() // 1 = Monday,... 7 = Sunday
	def monthNum = today.format("MM", location.timeZone).toInteger()
	def yearNum = today[Calendar.YEAR]
	//def yearNum = today.format("YYYY", location.timeZone).toInteger()  DOES NOT WORK

	if(hm.cDy == null || hm.cDy < 1 || hm.cDy > 7) {
		Logger("hm.cDy is invalid (${hm.cDy})", "error")
		return
	}

	if(dayNum == null || dayNum < 1 || dayNum > 7) {
		Logger("dayNum is invalid (${dayNum})", "error")
		return
	}

	if(monthNum == null || monthNum < 1 || monthNum > 12) {
		Logger("monthNum is invalid (${monthNum})", "error")
		return
	}

	LogTrace("updateOperatingHistory: dayNum: ${dayNum} cDy ${hm.cDy} | monthNum: ${monthNum} curMon ${hm.curMon} | yearNum: ${yearNum} curYr: ${hm.curYr}")

	if(dayNum != hm.cDy) {
		dayChange = true
	}
	if(monthNum != hm.curMon) {
		monthChange = true
	}
	if(yearNum != hm.curYr) {
		yearChange = true
	}


	if(dayChange) {
			stateRemove("obs") // get new forecasts
//		try {
			long Op_cusage = getSumUsage(state."oprStTblYest${dev.id}", "cooling")
			long Op_husage = getSumUsage(state."oprStTblYest${dev.id}", "heating")
			long OpIdle = getSumUsage(state."oprStTblYest${dev.id}", "idle")
			long Op_fo = getSumUsage(state."oprStTblYest${dev.id}", "fan only")
			long FanOn = getSumUsage(state."fanTblYest${dev.id}", "on")
			long FanAuto = getSumUsage(state."fanTblYest${dev.id}", "auto")

			Logger("FanOn ${FanOn} FanAuto: ${FanAuto} OpIdle: ${OpIdle} cool: ${Op_cusage} heat: ${Op_husage} fanonly: ${Op_fo}")

			hm."OpSt_Day${hm.cDy}_c" = Op_cusage
			hm."OpSt_Day${hm.cDy}_h" = Op_husage
			hm."OpSt_Day${hm.cDy}_i" = OpIdle
			hm."OpSt_Day${hm.cDy}_fo" = Op_fo
			hm."FanM_Day${hm.cDy}_On" = FanOn
			hm."FanM_Day${hm.cDy}_auto" = FanAuto

			hm.cDy = dayNum
			hm.OpSt_DWago_c = hm."OpSt_Day${hm.cDy}_c"
			hm.OpSt_DWago_h = hm."OpSt_Day${hm.cDy}_h"
			hm.OpSt_DWago_i = hm."OpSt_Day${hm.cDy}_i"
			hm.OpSt_DWago_fo = hm."OpSt_Day${hm.cDy}_fo"
			hm.FanM_DWago_On = hm."FanM_Day${hm.cDy}_On"
			hm.FanM_DWago_auto = hm."FanM_Day${hm.cDy}_auto"
			hm."OpSt_Day${hm.cDy}_c" = 0L
			hm."OpSt_Day${hm.cDy}_h" = 0L
			hm."OpSt_Day${hm.cDy}_i" = 0L
			hm."OpSt_Day${hm.cDy}_fo" = 0L
			hm."FanM_Day${hm.cDy}_On" = 0L
			hm."FanM_Day${hm.cDy}_auto" = 0L

			long t1 = hm?."OpSt_M${hm.curMon}_c"?.toInteger() ?: 0L
			hm."OpSt_M${hm.curMon}_c" = t1 + Op_cusage
			t1 = hm?."OpSt_M${hm.curMon}_h"?.toInteger() ?: 0L
			hm."OpSt_M${hm.curMon}_h" = t1 + Op_husage
			t1 = hm?."OpSt_M${hm.curMon}_i"?.toInteger() ?: 0L
			hm."OpSt_M${hm.curMon}_i" = t1 + OpIdle
			t1 = hm?."OpSt_M${hm.curMon}_fo"?.toInteger() ?: 0L
			hm."OpSt_M${hm.curMon}_fo" = t1 + Op_fo
			t1 = hm?."FanM_M${hm.curMon}_On"?.toInteger() ?: 0L
			hm."FanM_M${hm.curMon}_On" = t1 + FanOn
			t1 = hm?."FanM_M${hm.curMon}_auto"?.toInteger() ?: 0L
			hm."FanM_M${hm.curMon}_auto" = t1 + FanAuto

			if(monthChange) {
				hm.curMon = monthNum
				hm.OpSt_MYago_c = hm."OpSt_M${hm.curMon}_c"
				hm.OpSt_MYago_h = hm."OpSt_M${hm.curMon}_h"
				hm.OpSt_MYago_i = hm."OpSt_M${hm.curMon}_i"
				hm.OpSt_MYago_fo = hm."OpSt_M${hm.curMon}_fo"
				hm.FanM_MYago_On = hm."FanM_M${hm.curMon}_On"
				hm.FanM_MYago_auto = hm."FanM_M${hm.curMon}_auto"
				hm."OpSt_M${hm.curMon}_c" = 0L
				hm."OpSt_M${hm.curMon}_h" = 0L
				hm."OpSt_M${hm.curMon}_i" = 0L
				hm."FanM_M${hm.curMon}_On" = 0L
				hm."FanM_M${hm.curMon}_auto" = 0L
			}

			t1 = hm?.OpSt_thisY_c?.toInteger() ?: 0L
			hm.OpSt_thisY_c = t1 + Op_cusage
			t1 = hm?.OpSt_thisY_h?.toInteger() ?: 0L
			hm.OpSt_thisY_h = t1 + Op_husage
			t1 = hm?.OpSt_thisY_i?.toInteger() ?: 0L
			hm.OpSt_thisY_i = t1 + OpIdle
			t1 = hm?.OpSt_thisY_fo?.toInteger() ?: 0L
			hm.OpSt_thisY_fo = t1 + Op_fo
			t1 = hm?.FanM_thisY_On?.toInteger() ?: 0L
			hm.FanM_thisY_On = t1 + FanOn
			t1 = hm?.FanM_thisY_auto?.toInteger() ?: 0L
			hm.FanM_thisY_auto = t1 + FanAuto

			if(yearChange) {
				hm.curYr = yearNum
				hm.OpSt_lastY_c = hm.OpSt_thisY_c
				hm.OpSt_lastY_h = hm.OpSt_thisY_h
				hm.OpSt_lastY_i = hm.OpSt_thisY_i
				hm.OpSt_lastY_fo = hm.OpSt_thisY_fo
				hm.FanM_lastY_On = hm.FanM_thisY_On
				hm.FanM_lastY_auto = hm.FanM_thisY_auto

				hm.OpSt_thisY_c = 0L
				hm.OpSt_thisY_h = 0L
				hm.OpSt_thisY_i = 0L
				hm.OpSt_thisY_fo = 0L
				hm.FanM_thisY_On = 0L
				hm.FanM_thisY_auto = 0L
			}
			state."thermStor${dev.id}" = hm

/*
		} catch (ex) {
			//state.eric = 0 // force clear of stats
			//state.remove("thermStor${dev.id}")
			log.error "updateOperatingHistory Exception:", ex
		}
*/
	}
}


def getSumUsage(table, String strtyp) {
	//log.trace "getSumUsage...$strtyp Table size: ${table?.size()}"
	long totseconds = 0L
	long newseconds = 0L

	def hr
	def mins
	def myval
	def lasthr = 0
	def lastmins = 0
	def counting = false
	def firsttime = true
	def strthr
	def strtmin
	table.sort { a, b ->
		a[0] as Integer <=> b[0] as Integer ?: a[1] as Integer <=> b[1] as Integer ?: a[2] <=> b[2]
	}
	//log.trace "$table"
	table.each() {
		hr = it[0].toInteger()
		mins = it[1].toInteger()
		myval = it[2].toString()
		//log.debug "${it[0]} ${it[1]} ${it[2]}"
		if(myval == strtyp) {
			if(!counting) {
				strthr = firstime ? lasthr : hr
				strtmin = firsttime ? lastmins : mins
				counting = true
			}
		} else if(counting) {
			newseconds = ((hr * 60 + mins) - (strthr * 60 + strtmin)) * 60
			totseconds += newseconds
			counting = false
			//log.debug "found $strtyp	starthr: $strthr startmin: $strtmin newseconds: $newseconds	totalseconds: $totseconds"
		}
		firsttime = false
	}
	if(counting) {
		def newDate = new Date()
		lasthr = newDate.format("H", location.timeZone).toInteger()
		lastmins = newDate.format("m", location.timeZone).toInteger()
		if( (hr*60+mins > lasthr*60+lastmins) ) {
			lasthr = 24
			lastmins = 0
		}
		newseconds = ((lasthr * 60 + lastmins) - (strthr * 60 + strtmin)) * 60
		totseconds += newseconds
		//log.debug "still counting found $strtyp lasthr: $lasthr	lastmins: $lastmins starthr: $strthr startmin: $strtmin newseconds: $newseconds	totalseconds: $totseconds"
	}
	//log.info "$strtyp totseconds: $totseconds"

	return totseconds
}


void initHistoryStore(dev) {
	LogTrace("initHistoryStore($dev.id)...", "trace")

	def mytimeZone = location.timeZone

	def today = new Date()
	def dayNum = today.format("u", mytimeZone) as Integer // 1 = Monday,... 7 = Sunday
	def monthNum = today.format("MM", mytimeZone) as Integer
	def yearNum = today[Calendar.YEAR]
	//def yearNum = today.format("YYYY", mytimeZone) as Integer DOES NOT WORK

	LogTrace("initHIstoryStore: dayNum: ${dayNum} | monthNum: ${monthNum} | yearNum: ${yearNum} ")
	//dayNum = 6	// ERSTODO DEBUGGING

	def thermStor = [ "cDy": dayNum, "curMon": monthNum, "curYr": yearNum, //"tz": mytimeZone,
		OpSt_DWago_c: 0L, OpSt_DWago_h: 0L, OpSt_DWago_i: 0L, OpSt_DWago_fo: 0L,
		OpSt_MYago_c: 0L, OpSt_MYago_h: 0L, OpSt_MYago_i: 0L, OpSt_MYago_fo: 0L,
		OpSt_thisY_c: 0L, OpSt_thisY_h: 0L, OpSt_thisY_i: 0L, OpSt_thisY_fo: 0L,
		OpSt_lastY_c: 0L, OpSt_lastY_h: 0L, OpSt_lastY_i: 0L, OpSt_lastY_fo: 0L,
		FanM_DWago_On: 0L, FanM_DWago_auto: 0L,
		FanM_MYago_On: 0L, FanM_MYago_auto: 0L,
		FanM_thisY_On: 0L, FanM_thisY_auto: 0L,
		FanM_lastY_On: 0L, FanM_lastY_auto: 0L
	]

	for(int i = 1; i <= 7; i++) {
		thermStor << ["OpSt_Day${i}_c": 0L, "OpSt_Day${i}_h": 0L, "OpSt_Day${i}_i": 0L, "OpSt_Day${i}_fo": 0L]
		thermStor << ["FanM_Day${i}_On": 0L, "FanM_Day${i}_auto": 0L]
	}

	for(int i = 1; i <= 12; i++) {
		thermStor << ["OpSt_M${i}_c": 0L, "OpSt_M${i}_h": 0L, "OpSt_M${i}_i": 0L, "OpSt_M${i}_fo": 0L]
		thermStor << ["FanM_M${i}_On": 0L, "FanM_M${i}_auto": 0L]
	}

	//log.debug "initHistoryStore	thermStor${dev.id}: $thermStor"
	state."thermStor${dev.id}" = thermStor
}

def showChartHtml(devNum="", dev) {
	def tempStr = getTempUnitStr()

	def canHeat = getCanHeat(dev)
//Logger("showChart 0")
	def canCool = getCanCool(dev)
	def hasFan = getHasFan(dev)
	def has_weather = extWeatTempAvail()
	def commastr = has_weather ? "," : ""
//Logger("showChart 1")
	def coolstr1
	def coolstr2
	def coolstr3
	if(canCool) {
		coolstr1 = "data.addColumn('number', 'CoolSP');"
		coolstr2 = getDataTString(5,dev)
		coolstr3 = "4: {targetAxisIndex: 1, type: 'line', color: '#85AAFF', lineWidth: 1},"
	}

	def heatstr1
	def heatstr2
	def heatstr3
//Logger("showChart 2")
	if(canHeat) {
		heatstr1 = "data.addColumn('number', 'HeatSP');"
		heatstr2 = getDataTString(6,dev)
		heatstr3 = "5: {targetAxisIndex: 1, type: 'line', color: '#FF4900', lineWidth: 1}${commastr}"
	}

	def weathstr1 = "data.addColumn('number', 'ExtTmp');"
	def weathstr2 = getDataTString(7,dev)
	def weathstr3 = "6: {targetAxisIndex: 1, type: 'line', color: '#000000', lineWidth: 1}"
	if(state?.has_weather) {
		weathstr1 = "data.addColumn('number', 'ExtTmp');"
		weathstr2 = getDataTString(7,dev)
		weathstr3 = "6: {targetAxisIndex: 1, type: 'line', color: '#000000', lineWidth: 1}"
	}

	if(canCool && !canHeat) { coolstr3 = "4: {targetAxisIndex: 1, type: 'line', color: '#85AAFF', lineWidth: 1}${commastr}" }

	if(!canCool && canHeat) { heatstr3 = "4: {targetAxisIndex: 1, type: 'line', color: '#FF4900', lineWidth: 1}${commastr}" }

	if(!canCool) {
		coolstr1 = ""
		coolstr2 = ""
		coolstr3 = ""
		weathstr3 = "5: {targetAxisIndex: 1, type: 'line', color: '#000000', lineWidth: 1}"
	}

	if(!canHeat) {
		heatstr1 = ""
		heatstr2 = ""
		heatstr3 = ""
		weathstr3 = "5: {targetAxisIndex: 1, type: 'line', color: '#000000', lineWidth: 1}"
	}

	if(!has_weather) {
		weathstr1 = ""
		weathstr2 = ""
		weathstr3 = ""
	}
//Logger("showChart 3")

	def minval
	minval = has_weather ? getMinTemp("tempTblYest${dev.id}", "tempTbl${dev.id}", "eTempTbl") : getMinTemp("tempTblYest${dev.id}", "tempTbl${dev.id}")
	//def minval = getTMinTemp()
	def minstr = "minValue: ${minval},"

	//def minval = has_weather ? getMinTemp("tempTblYest", "tempTbl", "eTempTbl") : getMinTemp("tempTblYest", "tempTbl")
	def maxval
	maxval = has_weather ? getMaxTemp("tempTblYest${dev.id}", "tempTbl${dev.id}", "eTempTbl") : getMaxTemp("tempTblYest${dev.id}", "tempTbl${dev.id}")
	//def maxval = getTMaxTemp()
	def maxstr = "maxValue: ${maxval},"

	def differ = maxval - minval
	minstr = "minValue: ${(minval - (wantMetric() ? 2:5))},"
	maxstr = "maxValue: ${(maxval + (wantMetric() ? 2:5))},"
//Logger("showChart 4")

	def uData = getTodaysUsage(dev)
	def thData = (uData?.heating?.tSec.toLong()/3600).toDouble().round(0)
	def tcData = (uData?.cooling?.tSec.toLong()/3600).toDouble().round(0)
	def tiData = (uData?.idle?.tSec.toLong()/3600).toDouble().round(0)
	def tfData = (uData?.fanonly?.tSec.toLong()/3600).toDouble().round(0)
	def tfoData = (uData?.fanOn?.tSec.toLong()/3600).toDouble().round(0)
	def tfaData = (uData?.fanAuto?.tSec.toLong()/3600).toDouble().round(0)
//Logger("showChart 5")

	//Month Chart Section
	uData = getMonthsUsage(null, dev)
	def mhData = (uData?.heating?.tSec.toLong()/3600).toDouble().round(0)
	def mcData = (uData?.cooling?.tSec.toLong()/3600).toDouble().round(0)
	def miData = (uData?.idle?.tSec.toLong()/3600).toDouble().round(0)
	def mfData = (uData?.fanonly?.tSec.toLong()/3600).toDouble().round(0)
	def mfoData = (uData?.fanOn?.tSec.toLong()/3600).toDouble().round(0)
	def mfaData = (uData?.fanAuto?.tSec.toLong()/3600).toDouble().round(0)
//Logger("showChart 5a")

	def useTabListSize = 0
	if(canHeat) { useTabListSize = useTabListSize+1 }
	if(canCool) { useTabListSize = useTabListSize+1 }
	if(hasFan) { useTabListSize = useTabListSize+1 }
	def lStr = ""
	//Last 3 Months and Today Section
	def grpUseData = getLast3MonthsUsageMap(dev)
	def m1Data = []
	def m2Data = []
	def m3Data = []
//ERSTODO fix for fanonly
//Logger("showChart 6")
	grpUseData?.each { mon ->
		def data = mon?.value
		def heat = data?.heating ? (data?.heating?.tSec.toLong()/3600).toDouble().round(0) : 0
		def cool = data?.cooling ? (data?.cooling?.tSec.toLong()/3600).toDouble().round(0) : 0
		def idle = data?.idle ? (data?.idle?.tSec.toLong()/3600).toDouble().round(0) : 0
		def fanonly = data?.fanonly ? (data?.fanonly?.tSec.toLong()/3600).toDouble().round(0) : 0
		def fanOn = data?.fanOn ? (data?.fanOn?.tSec.toLong()/3600).toDouble().round(0) : 0
		def fanAuto = data?.fanAuto ? (data?.fanAuto?.tSec.toLong()/3600).toDouble().round(0) : 0
		def mName = getMonthNumToStr(mon?.key)
		lStr += "\n$mName Usage - Idle: ($idle) | Heat: ($heat) | Cool: ($cool) | Fanonly: (${fanonly}) FanOn: ($fanOn) | FanAuto: ($fanAuto)"
		def iNum = 1
		if(data?.idle?.iNum) { iNum = data?.idle?.iNum.toInteger() }
		else if(data?.heating?.iNum) {iNum = data?.heating?.iNum.toInteger() }
		else if(data?.cooling?.iNum == 1) { iNum = data?.cooling?.iNum.toInteger() }
		else if(data?.fanonly?.iNum == 1) { iNum = data?.fanonly?.iNum.toInteger() }
		else if(data?.fanOn?.iNum == 1) { iNum = data?.fanOn?.iNum.toInteger() }

		if(iNum == 1) {
			m1Data.push("'$mName'")
			if(canHeat) { m1Data.push("${heat}") }
			if(canCool) { m1Data.push("${cool}") }
			if(hasFan) { m1Data.push("${fanOn}") }
		 }
		if(iNum == 2) {
			m2Data.push("'$mName'")
			if(canHeat) { m2Data.push("${heat}") }
			if(canCool) { m2Data.push("${cool}") }
			if(hasFan) { m2Data.push("${fanOn}") }
		}
		if(iNum == 3) {
			m3Data.push("'$mName'")
			if(canHeat) { m3Data.push("${heat}") }
			if(canCool) { m3Data.push("${cool}") }
			if(hasFan) { m3Data.push("${fanOn}") }
		}
	}
//Logger("showChart 7")
	lStr += "\nToday's Usage - Idle: ($tiData) | Heat: ($thData) | Cool: ($tcData) | FanOn: ($tfoData) | FanAuto: ($tfaData)"
	def mUseHeadStr = ["'Month'"]
	if(canHeat) { mUseHeadStr.push("'Heat'") }
	if(canCool) { mUseHeadStr.push("'Cool'") }
	if(hasFan) { mUseHeadStr.push("'FanOn'") }

	def tdData = ["'Today'"]
	if(canHeat) { tdData.push("${thData}") }
	if(canCool) { tdData.push("${tcData}") }
	if(hasFan) { tdData.push("${tfoData}") }
	lStr += "\nToday Data List: $tdData\n\n"
//Logger("showChart 8")

	LogTrace("showChart: " + lStr)

	def data = """
		<script type="text/javascript">
			google.charts.load('current', {packages: ['corechart']});
			google.charts.setOnLoadCallback(drawHistoryGraph${devNum});
			google.charts.setOnLoadCallback(drawUseGraph${devNum});

			function drawHistoryGraph${devNum}() {
				var data = new google.visualization.DataTable();
				data.addColumn('timeofday', 'time');
				data.addColumn('number', 'Temp (Y)');
				data.addColumn('number', 'Temp (T)');
				data.addColumn('number', 'Operating');
				data.addColumn('number', 'Humidity');
				${coolstr1}
				${heatstr1}
				${weathstr1}
				data.addRows([
					${getDataTString(1,dev)}
					${getDataTString(2,dev)}
					${getDataTString(3,dev)}
					${getDataTString(4,dev)}
					${coolstr2}
					${heatstr2}
					${weathstr2}
				]);
				var options = {
					width: '100%',
					height: '100%',
					animation: {
						duration: 1500,
						startup: true
					},
					hAxis: {
						format: 'H:mm',
						minValue: [${getStartTime("tempTbl${dev.id}", "tempTblYest${dev.id}")},0,0],
						slantedText: true,
						slantedTextAngle: 30
					},
					series: {
						0: {targetAxisIndex: 1, type: 'area', color: '#FFC2C2', lineWidth: 1},
						1: {targetAxisIndex: 1, type: 'area', color: '#FF0000'},
						2: {targetAxisIndex: 0, type: 'area', color: '#ffdc89'},
						3: {targetAxisIndex: 0, type: 'area', color: '#B8B8B8'},
						${coolstr3}
						${heatstr3}
						${weathstr3}
					},
					vAxes: {
						0: {
							title: 'Humidity (%)',
							format: 'decimal',
							minValue: 0,
							maxValue: 100,
							textStyle: {color: '#B8B8B8'},
							titleTextStyle: {color: '#B8B8B8'}
						},
						1: {
							title: 'Temperature (${tempStr})',
							format: 'decimal',
							${minstr}
							${maxstr}
							textStyle: {color: '#FF0000'},
							titleTextStyle: {color: '#FF0000'}
						}
					},
					legend: {
						position: 'bottom',
						maxLines: 4,
						textStyle: {color: '#000000'}
					},
					chartArea: {
						left: '12%',
						right: '18%',
						top: '3%',
						bottom: '27%',
						height: '80%',
						width: '100%'
					}
				};
				var chart = new google.visualization.ComboChart(document.getElementById('main_graph${devNum}'));
				chart.draw(data, options);
			}

			function drawUseGraph${devNum}() {
				var data = google.visualization.arrayToDataTable([
				  ${mUseHeadStr},
				  ${tdData?.size() ? "${tdData}," : ""}
				  ${m3Data?.size() ? "${m3Data}${(m2Data?.size() || m1Data?.size() || tdData?.size()) ? "," : ""}" : ""}
				  ${m2Data?.size() ? "${m2Data}${(m1Data?.size() || tdData?.size())  ? "," : ""}" : ""}
				  ${m1Data?.size() ? "${m1Data}" : ""}
				]);

				var view = new google.visualization.DataView(data);
				view.setColumns([
					${(useTabListSize >= 1) ? "0," : ""}
					${(useTabListSize >= 1) ? "1, { calc: 'stringify', sourceColumn: 1, type: 'string', role: 'annotation' }${(useTabListSize > 1) ? "," : ""} // Heat Column": ""}
					${(useTabListSize > 1) ? "2, { calc: 'stringify', sourceColumn: 2, type: 'string', role: 'annotation' }${(useTabListSize > 2) ? "," : ""} // Cool column" : ""}
					${(useTabListSize > 2) ? "3, { calc: 'stringify', sourceColumn: 3, type: 'string', role: 'annotation' } // FanOn Column" : ""}
				]);
				var options = {
					vAxis: {
					  title: 'Hours'
					},
					seriesType: 'bars',
					colors: ['#FF9900', '#0066FF', '#884ae5'],
					chartArea: {
					  left: '10%',
					  right: '5%',
					  top: '7%',
					  bottom: '10%',
					  height: '95%',
					  width: '100%'
					},
					legend: {
						position: 'bottom',
						maxLines: 4
					}
				};

				var columnWrapper = new google.visualization.ChartWrapper({
					chartType: 'ComboChart',
					containerId: 'use_graph${devNum}',
					dataTable: view,
					options: options
				});
				columnWrapper.draw()
			}
		  </script>
		  <div class="swiper-slide">
		  	<section class="sectionBg">
			  <h3>Event History</h3>
	  		  <div id="main_graph${devNum}" style="width: 100%; height: 425px;"></div>
			</section>
  		  </div>
  		  <div class="swiper-slide">
		  	<section class="sectionBg">
				<h3>Usage History</h3>
  				<div id="use_graph${devNum}" style="width: 100%; height: 425px;"></div>
			</section>
  		  </div>
	  """
	return data
}


//ERS

def getAppEndpointUrl(subPath) { return "${getFullApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }
def getLocalEndpointUrl(subPath) { return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }

def getAccessToken() {
	try {
		if(!state?.access_token) { state?.access_token = createAccessToken() }
		else { return true }
	}
	catch (ex) {
		def msg = "Error: OAuth is not Enabled for ${app?.name}!."
	//	sendPush(msg)
		log.error "getAccessToken Exception ${ex?.message}"
		LogAction("getAccessToken Exception | $msg", "warn", true)
		return false
	}
}

def enableOauth() {
	def params = [
			uri: "http://localhost:8080/app/edit/update?_action_update=Update&oauthEnabled=true&id=${app.appTypeId}",
			headers: ['Content-Type':'text/html;charset=utf-8']
	]
	try {
		httpPost(params) { resp ->
			//LogTrace("response data: ${resp.data}")
		}
	} catch (e) {
		log.debug "enableOauth something went wrong: ${e}"
	}
}

void resetAppAccessToken(reset) {
	if(reset != true) { return }
	LogAction("Resetting Access Token....", "info", true)
	//revokeAccessToken()
	state?.access_token = null
	state?.accessToken = null
	if(getAccessToken()) {
		LogAction("Reset Access Token... Successful", "info", true)
		settingUpdate("resetAppAccessToken", "false", "bool")
	}
}

def getSomeWData(dev, devpoll = false) {
	//LogTrace("getSomeWData ${app} ${dev.label} ${dev.id}")

	//def todayDay = new Date().format("dd",getTimeZone())
	def mytz = getTZ(dev)
	def tZ = myTz ? TimeZone.getTimeZone(mytz) : location.timeZone

	def todayDay = new Date().format("dd",tZ)

//Logger("getSomeWData: todayDay: ${todayDay} ${dev.id} ${tZ}")

	if (state?."WtempTbl${dev.id}" == null) {
		//getSomeOldData(devpoll)

		state."WtempTbl${dev.id}" = []
		state."WdewTbl${dev.id}" = []
		state."WhumTbl${dev.id}" = []
		addNewWData(dev, tZ)
	}

	def tempTbl = state?."WtempTbl${dev.id}"
	def dewTbl = state?."WdewTbl${dev.id}"
	def humTbl = state?."WhumTbl${dev.id}"

	if (state?."WtempTblYest${dev.id}"?.size() == 0) {
		state."WtempTblYest${dev.id}" = tempTbl
		state."WdewTblYest${dev.id}" = dewTbl
		state."WhumTblYest${dev.id}" = humTbl
	}

	if (!state?."Wtoday${dev.id}" || state."Wtoday${dev.id}" != todayDay) {
		state."Wtoday${dev.id}" = todayDay
		state."WdewTblYest${dev.id}" = dewTbl
		state."WtempTblYest${dev.id}" = tempTbl
		state."WhumTblYest${dev.id}" = humTbl

		state."WtempTbl${dev.id}" = []
		state."WdewTbl${dev.id}" = []
		state."WhumTbl${dev.id}" = []
	}
	addNewWData(dev, tZ)
}

def addNewWData(dev, tZ) {
	// add latest weather humidity, dewpoint & temperature readings for the graph
	def currentTemperature = getTemp(dev)
	def currentDewpoint = getDewpoint(dev)
	def currentHumidity = getHumidity(dev)

	def tempTbl = state?."WtempTbl${dev.id}"
	def dewTbl = state?."WdewTbl${dev.id}"
	def humTbl = state?."WhumTbl${dev.id}"


	def newDate = new Date()
	if(newDate == null) { Logger("got null for new Date()") }

	def hr = newDate.format("H", location.timeZone) as Integer
	def mins = newDate.format("m", location.timeZone) as Integer

//Logger("addNewWData currentTemp: ${currentTemperature}	WtempTbl: ${tempTbl}", "trace")
//Logger("addNewWData ${dev.id} ${tZ} hr: ${hr} mins: ${mins}", "trace")

	state."WtempTbl${dev.id}" =	addValue(tempTbl, hr, mins, currentTemperature)
	state."WdewTbl${dev.id}" =	addValue(dewTbl, hr, mins, currentDewpoint)
	state?."WhumTbl${dev.id}" =	addValue(humTbl, hr, mins, currentHumidity)
}

def addValue(table, hr, mins, val) {
	def newTable = table
	if(table?.size() > 2) {
		def last = table.last()[2]
		def secondtolast = table[-2][2]
		if(val == last && val == secondtolast) {
			newTable = table.take(table.size() - 1)
		}
	}
	newTable.add([hr, mins, val])
	return newTable
}

// getStartTime("dewTbl", "dewTblYest"))
def getStartTime(tbl1, tbl2) {
	def startTime = 24
	if (state?."${tbl1}"?.size()) {
		startTime = state."${tbl1}".min{it[0].toInteger()}[0].toInteger()
	}
	if (state?."${tbl2}"?.size()) {
		startTime = Math.min(startTime, state."${tbl2}".min{it[0].toInteger()}[0].toInteger())
	}
	return startTime
}

// getMinTemp("tempTblYest", "tempTbl", "dewTbl", "dewTblYest"))
def getMinTemp(tbl1, tbl2, tbl3=null, tbl4=null) {
	def list = []
	if (state?."${tbl1}"?.size() > 0) { list.add(state?."${tbl1}"?.min { it[2] }[2]) }
	if (state?."${tbl2}"?.size() > 0) { list.add(state?."${tbl2}".min { it[2] }[2]) }
	if (state?."${tbl3}"?.size() > 0) { list.add(state?."${tbl3}".min { it[2] }[2]) }
	if (state?."${tbl4}"?.size() > 0) { list.add(state?."${tbl4}".min { it[2] }[2]) }
	//LogAction("getMinTemp: ${list.min()} result: ${list}", "trace")
	return list?.min()
}

// getMaxTemp("tempTblYest", "tempTbl", "dewTbl", "dewTblYest"))
def getMaxTemp(tbl1, tbl2, tbl3=null, tbl4=null) {
	def list = []
	if (state?."${tbl1}"?.size() > 0) { list.add(state?."${tbl1}".max { it[2] }[2]) }
	if (state?."${tbl2}"?.size() > 0) { list.add(state?."${tbl2}".max { it[2] }[2]) }
	if (state?."${tbl3}"?.size() > 0) { list.add(state?."${tbl3}".max { it[2] }[2]) }
	if (state?."${tbl4}"?.size() > 0) { list.add(state?."${tbl4}".max { it[2] }[2]) }
	//LogAnction("getMaxTemp: ${list.max()} result: ${list}", "trace")
	return list?.max()
}


// weather ERS

def getWDeviceTile(devNum="", dev) {
	def obs = getApiXUData(dev)
//	try {
		if(!obs) { //state?.curWeather || !state?.curForecast) {
			return hideWeatherHtml()
		}
//Logger("W1")
		def updateAvail = !state.updateAvailable ? "" : """<div class="greenAlertBanner">Device Update Available!</div>"""
		def clientBl = state?.clientBl ? """<div class="brightRedAlertBanner">Your Manager client has been blacklisted!\nPlease contact the Nest Manager developer to get the issue resolved!!!</div>""" : ""
		def obsrvTime = "Last Updated:\n${dev?.currentState("last_updated").value}"
//Logger("W2")

		def mainHtml = """
			<div class="device">
				<div class="container">
					<h4>Current Weather Conditions</h4>
					<h1 class="bottomBorder"> ${dev?.currentState("location").value} </h1>
					<div class="row">
						<div class="six columns">
							<b>Feels Like:</b> ${getFeelslike(dev)} <br>
	<!--						<b>Precip %: </b> ${dev.currentState("percentPrecip")?.value}% <br> -->
							<b>Precip: </b> ${getPrecip(dev)} <br>
							<b>Humidity:</b> ${getHumidity(dev)}% <br>
							<b>Dew Point: </b>${getDewpoint(dev)}<br>
							<b>Pressure: </b> ${getPressure(dev)} <br>
							<b>UV Index: </b>${dev.currentState("ultravioletIndex")?.value}<br>
							<b>Visibility:</b> ${getVisibility(dev)} <br>
							<b>Lux:</b> ${getLux(dev)}<br>
							<b>Sunrise:</b> ${obs.forecast.forecastday[0].astro.sunrise} <br> <b>Sunset: </b> ${obs.forecast.forecastday[0].astro.sunset} <br>
							<b>Wind:</b> ${getWind(dev)} <br>
							<b>Moon Phase:</b> ${getMoonPhase(0, obs)} <br>
						</div>
						<div class="six columns">
							<img class="offset-by-two eight columns" src="${getWeatherImg(obs.current.condition.code)}"> <br>
							<h2>${getTemp(dev)}</h2>
							<h1 class ="offset-by-two topBorder">${obs?.current?.condition?.text}</h1>
						</div>
					</div>
					<div class="row topBorder">
						<div class="centerText four columns">${forecastDay(0, obs)}</div>
						<div class="centerText four columns">${forecastDay(1, obs)}</div>
						<div class="centerText four columns">${forecastDay(2, obs)}</div>
					</div>
					<div class="row">
						<div class="centerText four columns">${forecastDay(3, obs)}</div>
						<div class="centerText four columns">${forecastDay(4, obs)}</div>
						<div class="centerText four columns">${forecastDay(5, obs)}</div>
					</div>
					<p style="font-size: 12px; font-weight: normal; text-align: center;">Tap Icon to View Forecast</p>


					${historyGraphHtml(devNum,dev)}

					<div class="row topBorder">
						<div class="centerText offset-by-three six columns">
<!--							<b class="wStation">${state?.curWeather?.validTimeLocal}</b> -->
						</div>
					</div>
				</div>
			</div>

		"""
//		Logger("getMoonPhase: ${getMoonPhase(0, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(1, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(2, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(3, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(4, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(5, obs)}")
//		Logger("getMoonPhase: ${getMoonPhase(6, obs)}")
/* """ */
//		render contentType: "text/html", data: mainHtml, status: 200
/*
	}
	catch (ex) {
		log.error "getDeviceTile Exception:", ex
		//exceptionDataHandler(ex?.message, "getDeviceTile")
	}
*/
}

def getMoonPhase(day, obs) {
//Logger("getMoon")
	def astro = obs.forecast.forecastday[day].astro
	LogTrace("day $day  astro: ${astro}")

	def t0 = "${obs.forecast.forecastday[day].date} ${astro.sunrise}"
	def sunRise = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()

	t0 = "${obs.forecast.forecastday[day].date} ${astro.sunset}"
	def sunSet = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()

	def moonRise
	if(astro.moonrise == "No moonrise") {
		t0 = "${obs.forecast.forecastday[day].date} 00:01 AM"
		moonRise = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime() - 30*60*1000 // subtract 30 mins
	} else {
		t0 = "${obs.forecast.forecastday[day].date} ${astro.moonrise}"
		moonRise = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()
	}

	def moonSet
	if(astro.moonset == "No moonset") {
		t0 = "${obs.forecast.forecastday[day].date} 11:59 PM"
		moonSet = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime() + 30*60*1000 // add 30 mins
	} else {
		t0 = "${obs.forecast.forecastday[day].date} ${astro.moonset}"
		moonSet = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()
	}

	t0 = "${obs.forecast.forecastday[day].date} 00:00 AM"
	def localMidNight = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()
//	def localMidNightf = formatDt(Date.parse("yyyy-MM-dd hh:mm a", t0))
//	Logger("localMidNight: ${localMidNightf}")

	t0 = "${obs.forecast.forecastday[day].date} 12:00 PM"
	def localNoon = Date.parse("yyyy-MM-dd hh:mm a", t0).getTime()

	t0 =  24/8*3600*1000
	def t1 =  24/8*3600*1000 * 0.333

	def compare = [ Math.abs(sunRise + t1 - moonRise)/(1000), 		// New Moon
			Math.abs(sunRise + t0 + t1 - moonRise)/(1000),	// Waxing crescent
			Math.abs(localNoon + t1 - moonRise)/(1000),		// First Quarter
			Math.abs(localNoon + t0 + t1 - moonRise)/(1000),	// Waxing gibbous
			Math.abs(sunSet + t1 - moonRise)/(1000),		// Full
			Math.abs(sunSet + t0 + t1 - moonRise)/(1000),	// Waning gibbous
			Math.abs(localMidNight + t1 - moonRise)/(1000), 	// 3rd Quarter
			Math.abs(localMidNight + t0 + t1 - moonRise)/(1000) 	// Waning cresent
	]
	def nearest = compare.min()
	def indx = compare.indexOf(nearest)
	def tstr
	def brightness
	switch (indx) {
		case 0:
			tstr = "New Moon"
			brightness = 0
			break
		case 1:
			tstr = "Waxing cresent"
			brightness = 20
			break
		case 2:
			tstr = "1st Quarter"
			brightness = 40
			break
		case 3:
			tstr = "Waxing gibbous"
			brightness = 70
			break
		case 4:
			tstr = "Full"
			brightness = 100
			break
		case 5:
			tstr = "Waning gibbous"
			brightness = 70
			break
		case 6:
			tstr = "3rd Quarter"
			brightness = 40
			break
		case 7:
			tstr = "Waning cresent"
			brightness = 20
			break
	}
	LogTrace("moon: $day   ${compare[indx]}   $indx   $tstr  $compare")
	return tstr
}

def getWeatherImg(cond) {
	def aa = getWUIconName(cond,1)
		//def newCond = getWeatCondFromUrl(cond)
		//def url = "https://cdn.rawgit.com/tonesto7/nest-manager/master/Images/Weather/icons/black/${getWeatCondFromUrl(cond) ?: "unknown"}.svg"
// https://console.bluemix.net/docs/api/content/services/Weather/images/30.png
		def url = "https://cdn.rawgit.com/tonesto7/nest-manager/master/Images/Weather/icons/black/${aa ? "${aa}" : "unknown"}.svg"
		return url
}

def forecastDay(day, obs) {
//Logger("forecastDay $day")
	if(!obs) { return "no data"}
	def dayName = "<b>${obs.forecast.forecastday[day].date} </b><br>"
	def foreImgB64 = getWeatherImg(obs.forecast.forecastday[day].day.condition.code)
	def forecastImageLink = """<a class=\"${day}-modal\"><img src="${foreImgB64}" style="width:64px;height:64px;"></a><br>"""
	def forecastTxt = "<p>${obs.forecast.forecastday[day].day.condition.text}"
	def t0 = (!wantMetric() ? obs.forecast.forecastday[day].day.mintemp_f : obs.forecast.forecastday[day].day.mintemp_c)
	def t1 = (!wantMetric() ? obs.forecast.forecastday[day].day.maxtemp_f : obs.forecast.forecastday[day].day.maxtemp_c)
	forecastTxt += "<br>Temp low: ${t0}	Temp high: ${t1}"
	t0 = (!wantMetric() ? obs.forecast.forecastday[day].day.maxwind_mph : obs.forecast.forecastday[day].day.maxwind_kph)
	forecastTxt += "<br>Wind: ${t0}"
	t0 = (!wantMetric() ? obs.forecast.forecastday[day].day.totalprecip_in : obs.forecast.forecastday[day].day.totalprecip_mm)
	forecastTxt += "<br>Precipitation: ${t0}"
	t0 = "<br>Moon Phase: ${getMoonPhase(day, obs)}"
	forecastTxt += "${t0}"

	def modalHead = "<script> \$('.${day}-modal').click(function(){vex.dialog.alert({unsafeMessage: ' "
	def modalTitle = " <h2>${obs.forecast.forecastday[day].date}</h2>"
	def forecastImage = """<div class=\"centerText\"><img src="${foreImgB64}" style="width:64px;height:64px;"></div>"""
	forecastTxt += "</p>"
	def modalClose = "' }); }); </script>"

	return dayName + forecastImageLink + modalHead + modalTitle + forecastImage + forecastTxt + modalClose
}

def getWUIconName(condition_code, is_day)	{
	def cC = condition_code.toInteger()
	def wuIcon = (conditionFactor[cC] ? conditionFactor[cC][2] : '')
	if (is_day != 1 && wuIcon) wuIcon = 'nt_' + wuIcon;
	return wuIcon
}

@Field final Map	conditionFactor = [
	1000: ['Sunny', 1, 'sunny'],						1003: ['Partly cloudy', 0.8, 'partlycloudy'],
	1006: ['Cloudy', 0.6, 'cloudy'],					1009: ['Overcast', 0.5, 'cloudy'],
	1030: ['Mist', 0.5, 'fog'],						1063: ['Patchy rain possible', 0.8, 'chancerain'],
	1066: ['Patchy snow possible', 0.6, 'chancesnow'],			1069: ['Patchy sleet possible', 0.6, 'chancesleet'],
	1072: ['Patchy freezing drizzle possible', 0.4, 'chancesleet'],		1087: ['Thundery outbreaks possible', 0.2, 'chancetstorms'],
	1114: ['Blowing snow', 0.3, 'snow'],					1117: ['Blizzard', 0.1, 'snow'],
	1135: ['Fog', 0.2, 'fog'],						1147: ['Freezing fog', 0.1, 'fog'],
	1150: ['Patchy light drizzle', 0.8, 'rain'],				1153: ['Light drizzle', 0.7, 'rain'],
	1168: ['Freezing drizzle', 0.5, 'sleet'],				1171: ['Heavy freezing drizzle', 0.2, 'sleet'],
	1180: ['Patchy light rain', 0.8, 'rain'],				1183: ['Light rain', 0.7, 'rain'],
	1186: ['Moderate rain at times', 0.5, 'rain'],				1189: ['Moderate rain', 0.4, 'rain'],
	1192: ['Heavy rain at times', 0.3, 'rain'],				1195: ['Heavy rain', 0.2, 'rain'],
	1198: ['Light freezing rain', 0.7, 'sleet'],				1201: ['Moderate or heavy freezing rain', 0.3, 'sleet'],
	1204: ['Light sleet', 0.5, 'sleet'],					1207: ['Moderate or heavy sleet', 0.3, 'sleet'],
	1210: ['Patchy light snow', 0.8, 'flurries'],				1213: ['Light snow', 0.7, 'snow'],
	1216: ['Patchy moderate snow', 0.6, 'snow'],				1219: ['Moderate snow', 0.5, 'snow'],
	1222: ['Patchy heavy snow', 0.4, 'snow'],				1225: ['Heavy snow', 0.3, 'snow'],
	1237: ['Ice pellets', 0.5, 'sleet'],					1240: ['Light rain shower', 0.8, 'rain'],
	1243: ['Moderate or heavy rain shower', 0.3, 'rain'],			1246: ['Torrential rain shower', 0.1, 'rain'],
	1249: ['Light sleet showers', 0.7, 'sleet'],				1252: ['Moderate or heavy sleet showers', 0.5, 'sleet'],
	1255: ['Light snow showers', 0.7, 'snow'],				1258: ['Moderate or heavy snow showers', 0.5, 'snow'],
	1261: ['Light showers of ice pellets', 0.7, 'sleet'],			1264: ['Moderate or heavy showers of ice pellets',0.3, 'sleet'],
	1273: ['Patchy light rain with thunder', 0.5, 'tstorms'],		1276: ['Moderate or heavy rain with thunder', 0.3, 'tstorms'],
	1279: ['Patchy light snow with thunder', 0.5, 'tstorms'],		1282: ['Moderate or heavy snow with thunder', 0.3, 'tstorms']
]

def wantMetric() { return (getTemperatureScale() == "C") }

def getTempUnitStr() {
	def tempStr = "\u00b0F"
	if ( wantMetric() ) {
		tempStr = "\u00b0C"
	}
	return tempStr
}

def getFeelslike(dev) {
	return "${dev.currentState("feelsLike")?.value}${getTempUnitStr()}"
}

def getPrecip(dev) {
	def tstr = dev.currentState("precip_today")?.value.toString()
	if(wantMetric()) {
		return "${tstr} mm"
	} else {
		return "${tstr} in"
	}
}

def getPressure(dev) {
	def tstr = "" //	" " + device.currentState("pressure_trend")?.value.toString()
	def tstr1 = dev.currentState("pressure")?.value.toString()
	if(wantMetric()) {
		return "${tstr1} mb ${tstr}"
	} else {
		return "${tstr1} in ${tstr}"
	}
}

def getVisibility(dev) {
	def tstr = dev.currentState("visibility")?.value.toString()
	if(wantMetric()) {
		return "${tstr} km"
	} else {
		return "${tstr} Miles"
	}
}

def getLux(dev) {
	def cur = dev.currentState("illuminance")?.value.toString()
	return cur
}

def getWind(dev) {
	def cur = dev.currentState("wind")?.value.toString()
	def cur1 = dev.currentState("wind_dir")?.value.toString()
	return "${cur1} at ${cur} ${wantMetric() ? "Kph" : "Mph"}"
}

String getDataString(Integer seriesIndex, dev) {
	def dataString = ""
	def dataTable = []
	switch (seriesIndex) {
		case 1:
//			dataTable = state?."WtempTblYest${dev.id}"
			dataTable = state?."WtempTbl${dev.id}"
			break
		case 2:
//			dataTable = state?."WdewTblYest${dev.id}"
			dataTable = state?."WdewTbl${dev.id}"
			break
		case 3:
//			dataTable = state?."WtempTbl${dev.id}"
			dataTable = state?."WhumTbl${dev.id}"
			break
		case 4:
//			dataTable = state?."WdewTbl${dev.id}"
			dataTable = state?."WtempTblYest${dev.id}"
			break
		case 5:
//			dataTable = state?."WhumTblYest${dev.id}"
			dataTable = state?."WdewTblYest${dev.id}"
			break
		case 6:
//			dataTable = state?."WhumTbl${dev.id}"
			dataTable = state?."WhumTblYest${dev.id}"
			break
	}
	dataTable.each() {
		def dataArray = [[it[0],it[1],0],null,null,null,null,null,null]
		dataArray[seriesIndex] = it[2]
		dataString += dataArray?.toString() + ","
	}
	return dataString
}

def historyGraphHtml(devNum="", dev) {
//Logger("HistoryG 1")
	def html = ""
	if(true) {
		if (state?."WtempTbl${dev.id}"?.size() > 0 && state?."WdewTbl${dev.id}"?.size() > 0) {
			def tempStr = getTempUnitStr()
			def minval = getMinTemp("WtempTblYest${dev.id}", "WtempTbl${dev.id}", "WdewTbl${dev.id}", "WdewTblYest${dev.id}")
			def minstr = "minValue: ${minval},"
//Logger("HistoryG 1a")

			def maxval = getMaxTemp("WtempTblYest${dev.id}", "WtempTbl${dev.id}", "WdewTbl${dev.id}", "WdewTblYest${dev.id}")
			def maxstr = "maxValue: ${maxval},"
//Logger("HistoryG 1b")

			def differ = maxval - minval
			//LogAction("differ ${differ}", "trace")
			minstr = "minValue: ${(minval - (wantMetric() ? 2:5))},"
			maxstr = "maxValue: ${(maxval + (wantMetric() ? 2:5))},"
//Logger("HistoryG 2")

			html = """
			  <script type="text/javascript">
				google.charts.load('current', {packages: ['corechart']});
				google.charts.setOnLoadCallback(drawWeatherGraph);
				function drawWeatherGraph() {
					var data = new google.visualization.DataTable();
					data.addColumn('timeofday', 'time');
					data.addColumn('number', 'Temp (T)');
					data.addColumn('number', 'Dew (T)');
					data.addColumn('number', 'Hum (T)');
					data.addColumn('number', 'T (Y)');
					data.addColumn('number', 'D (Y)');
					data.addColumn('number', 'H (Y)');
					data.addRows([
						${getDataString(1, dev)}
						${getDataString(2, dev)}
						${getDataString(3, dev)}
						${getDataString(4, dev)}
						${getDataString(5, dev)}
						${getDataString(6, dev)}
					]);
					var options = {
						width: '100%',
						height: '100%',
						animation: {
							duration: 1500,
							startup: true
						},
						hAxis: {
							format: 'H:mm',
							minValue: [${getStartTime("WdewTbl${dev.id}", "WdewTblYest${dev.id}")},0,0],
							slantedText: true,
							slantedTextAngle: 30
						},
						series: {
							0: {targetAxisIndex: 1, color: '#FF0000'},
							1: {targetAxisIndex: 1, color: '#004CFF'},
							2: {targetAxisIndex: 0, color: '#B8B8B8'},
							3: {targetAxisIndex: 1, color: '#FFC2C2', lineWidth: 1},
							4: {targetAxisIndex: 1, color: '#D1DFFF', lineWidth: 1},
							5: {targetAxisIndex: 0, color: '#D2D2D2', lineWidth: 1}
						},
						vAxes: {
							0: {
								title: 'Humidity (%)',
								format: 'decimal',
								minValue: 0,
								maxValue: 100,
								textStyle: {color: '#B8B8B8'},
								titleTextStyle: {color: '#B8B8B8'}
							},
							1: {
								title: 'Temperature (${tempStr})',
								format: 'decimal',
								${minstr}
								${maxstr}
								textStyle: {color: '#FF0000'},
								titleTextStyle: {color: '#FF0000'}
							}
						},
						legend: {
							position: 'bottom',
							maxLines: 6,
							textStyle: {color: '#000000'}
						},
						chartArea: {
							left: '12%',
							right: '18%',
							top: '3%',
							bottom: '20%',
							height: '85%',
							width: '100%'
						}
					};
					var chart = new google.visualization.AreaChart(document.getElementById('chart_div${devNum}'));
					chart.draw(data, options);
				}
			</script>
			<h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
			<div id="chart_div${devNum}" style="width: 100%; height: 225px;"></div>
			"""
		} else {
			html = """
				<h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
				<br></br>
				<div class="centerText">
				<p>Waiting for more data to be collected</p>
				<p>This may take at a couple hours</p>
				</div>
			"""
		}
	}
}

def hideWeatherHtml() {
	def data = """
		<br></br><br></br>
		<h3 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">The Required Weather data is not available yet...</h3>
		<br></br><h3 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Please refresh this page after a couple minutes...</h3>
		<br></br><br></br>"""
//	render contentType: "text/html", data: data, status: 200
}



def getCarbonImg(b64=true, dev) {
	def carbonVal = dev.currentState("nestCarbonMonoxide")?.value
	//values in ST are tested, clear, detected
	//values from nest are ok, warning, emergency
	def img = ""
	def caption = "${carbonVal ? carbonVal?.toString().toUpperCase() : ""}"
	def captionClass = ""
	switch(carbonVal) {
		case "warning":
			img = getImg("co2_warn_status.png")
			captionClass = "alarmWarnCap"
			break
		case "emergency":
			img = getImg("co2_emergency_status.png")
			captionClass = "alarmEmerCap"
			break
		default:
			img = getImg("co2_clear_status.png")
			captionClass = "alarmClearCap"
			break
	}
	return ["img":img, "caption": caption, "captionClass":captionClass]
}

def getSmokeImg(b64=true, dev) {
	def smokeVal = dev.currentState("nestSmoke")?.value
	//values in ST are tested, clear, detected
	//values from nest are ok, warning, emergency
	def img = ""
	def caption = "${smokeVal ? smokeVal?.toString().toUpperCase() : ""}"
	def captionClass = ""
	switch(smokeVal) {
		case "warning":
			img = getImg("smoke_warn_status.png")
			captionClass = "alarmWarnCap"
			break
		case "emergency":
			img = getImg("smoke_emergency_status.png")
			captionClass = "alarmEmerCap"
			break
		default:
			img = getImg("smoke_clear_status.png")
			captionClass = "alarmClearCap"
			break
	}
	return ["img":img, "caption": caption, "captionClass":captionClass]
}


def getImg(imgName) {
	if(imgName) {
		return imgName ? "https://cdn.rawgit.com/tonesto7/nest-manager/master/Images/Devices/$imgName" : ""
	} else {
		log.error "getImg Error: Missing imgName value..."
	}
}


def getProtDeviceTile(devNum, dev) {
//	try {
		def battImg = (dev.currentState("batteryState")?.value == "replace") ? """<img class="battImg" src="${getImg("battery_low_h.png")}">""" : """<img class="battImg" src="${getImg("battery_ok_h.png")}">"""
		//def battImg = (state?.battVal == "low") ? """<img class="battImg" src="${getImg("battery_low_h.png")}">""" : """<img class="battImg" src="${getImg("battery_ok_h.png")}">"""

		def testVal = dev.currentState("isTesting")?.value
		def testModeHTML = (testVal.toString() == "true") ? "<h3>Test Mode</h3>" : ""
		def updateAvail = !state.updateAvailable ? "" : """<div class="greenAlertBanner">Device Update Available!</div>"""
		def clientBl = state?.clientBl ? """<div class="brightRedAlertBanner">Your Manager client has been blacklisted!\nPlease contact the Nest Manager developer to get the issue resolved!!!</div>""" : ""

		def smokeImg = getSmokeImg(false, dev)
		def carbonImg = getCarbonImg(false, dev)
		def onlineStatus = dev.currentState("onlineStatus")?.value
		def powerSource = dev.currentState("powerSource")?.value
		def apiStatus =	 dev.currentState("apiStatus")?.value
		def softwareVer = dev.currentState("softwareVer")?.value
		def lastConnection = dev.currentState("lastConnection")?.value
		def html = """
		  ${testModeHTML}
		  ${clientBl}
		  ${updateAvail}
		  <div class="device">
			  <section class="sectionBgTile">
				  <h3>Alarm Status</h3>
				  <table class="devInfoTile">
				    <col width="48%">
				    <col width="48%">
				    <thead>
					  <th>Smoke Detector</th>
					  <th>Carbon Monoxide</th>
				    </thead>
				    <tbody>
					  <tr>
					    <td>
							<img class='alarmImg' src="${smokeImg?.img}">
							<span class="${smokeImg?.captionClass}">${smokeImg?.caption}</span>
						</td>
					    <td>
							<img class='alarmImg' src="${carbonImg?.img}">
							<span class="${carbonImg?.captionClass}">${carbonImg?.caption}</span>
						</td>
					  </tr>
				    </tbody>
				  </table>
			  </section>
			  <br>
			  <section class="sectionBgTile">
			  	<h3>Device Info</h3>
				<table class="devInfoTile">
					<col width="33%">
					<col width="33%">
					<col width="33%">
					<thead>
					  <th>Network Status</th>
					  <th>Power Type</th>
					  <th>API Status</th>
					</thead>
					<tbody>
					  <tr>
					  <td${onlineStatus != "online" ? """ class="redText" """ : ""}>${onlineStatus.toString().capitalize()}</td>
					  <td>${powerSource != null ? powerSource.toString().capitalize() : "Not Available Yet"}</td>
					  <td${apiStatus != "Good" ? """ class="orangeText" """ : ""}>${apiStatus}</td>
					  </tr>
					</tbody>
				</table>
			</section>
			<section class="sectionBgTile">
				<table class="devInfoTile">
					<col width="50%">
					<col width="50%">
					<thead>
					  <th>Firmware Version</th>
					  <th>Last Check-In</th>
					</thead>
					<tbody>
					  <tr>
						<td>v${softwareVer.toString()}</td>
					  	<td class="dateTimeTextTile">${lastConnection.toString()}</td>
					  </tr>
					</tbody>
			  	</table>
			  </section>
			</div>
		"""
		return html
/*
	}
	catch (ex) {
		log.error "getDeviceTile Exception:", ex
		exceptionDataHandler(ex?.message, "getInfoHtml")
	}
*/
}




def getTimeZone() {
	def tz = null
	if(location?.timeZone) { tz = location?.timeZone }
	if(!tz) { LogAction("getTimeZone: Hub or Nest TimeZone not found", "warn", true) }
	return tz
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}

def formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("HE TimeZone is not set; Please open your location and Press Save", "warn", true)
	}
	return tf.format(dt)
}

def GetTimeDiffSeconds(strtDate, stpDate=null, methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		//if(strtDate?.contains("dtNow")) { return 10000 }
		def now = new Date()
		def stopVal = stpDate ? stpDate.toString() : formatDt(now)
		def start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		def diff = (int) (long) (stop - start) / 1000
		LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}


/************************************************************************************************
|									LOGGING AND Diagnostic									|
*************************************************************************************************/
def lastN(String input, n) {
	return n > input?.size() ? input : input[-n..-1]
}

def LogTrace(msg, logSrc=null) {
	def trOn = (showDebug && advAppDebug) ? true : false
	if(trOn) {
		//def theId = lastN(app?.id.toString(),5)
		//def theLogSrc = (logSrc == null) ? (parent ? "Automation-${theId}" : "NestManager") : logSrc
		Logger(msg, "trace", logSrc, state?.enRemDiagLogging)
	}
}

def LogAction(msg, type="debug", showAlways=false, logSrc=null) {
	def isDbg = showDebug ? true : false
//	def theId = lastN(app?.id.toString(),5)
//	def theLogSrc = (logSrc == null) ? (parent ? "Automation-${theId}" : "NestManager") : logSrc
	if(showAlways || (isDbg && !showAlways)) { Logger(msg, type, logSrc) }
}

def Logger(msg, type="debug", logSrc=null, noLog=false) {
	if(msg && type) {
		def labelstr = ""
		if(state?.dbgAppndName == null) {
			def tval = parent ? parent.getSettingVal("dbgAppndName") : settings?.dbgAppndName
			state?.dbgAppndName = (tval || tval == null) ? true : false
		}
		def t0 = app.label
		if(state?.dbgAppndName) { labelstr = "${app.label} | " }
		def themsg = "${labelstr}${msg}"
		//log.debug "Logger remDiagTest: $msg | $type | $logSrc"

		if(state?.enRemDiagLogging == null) {
			state?.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
			if(state?.enRemDiagLogging == null) {
				state?.enRemDiagLogging = false
			}
			//log.debug "set enRemDiagLogging to ${state?.enRemDiagLogging}"
		}
		if(state?.enRemDiagLogging) {
			def theId = lastN(app?.id.toString(),5)
			def theLogSrc = (logSrc == null) ? (parent ? "Automation-${theId}" : "NestManager") : logSrc
			parent?.saveLogtoRemDiagStore(themsg, type, theLogSrc)
		} else {
			if(!noLog) {
			switch(type) {
				case "debug":
					log.debug "${themsg}"
					break
				case "info":
					log.info "| ${themsg}"
					break
				case "trace":
					log.trace "| ${themsg}"
					break
				case "error":
					log.error "| ${themsg}"
					break
				case "warn":
					log.warn "|| ${themsg}"
					break
				default:
					log.debug "${themsg}"
					break
			}
			}
		}
	}
	else { log.error "${labelstr}Logger Error - type: ${type} | msg: ${msg} | logSrc: ${logSrc}" }
}
