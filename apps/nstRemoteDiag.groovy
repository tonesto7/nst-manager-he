/********************************************************************************************
|    Application Name: NST Diagnostics						            |
|    Copyright (C) 2017, 2018, 2019 Anthony S.						    |
|    Authors: Anthony S. (@tonesto7), Eric S. (@E_sch)				            |
|    Contributors: Ben W. (@desertblade)						    |
|    A few code methods are modeled from those in CoRE by Adrian Caramaliu		    |
|											    |
|    License Info: https://github.com/tonesto7/nest-manager/blob/master/app_license.txt     |
|********************************************************************************************/

import groovy.json.*
import java.text.SimpleDateFormat

definition(
	name: "NST Diagnostics",
	namespace: "tonesto7",
	author: "Anthony S.",
	parent: "tonesto7:NST Manager",
	description: "This App is used to enable built-in automations for NST Manager",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/apps/nstRemoteDiag.groovy",
	singleInstance: true,
	oauth: true)


def appVersion() { "2.0.1" }

preferences {
	page(name: "startPage")

	//Automation Pages
	page(name: "notAllowedPage")
	page(name: "selectAutoPage")
	page(name: "mainAutoPage")
	//page(name: "mainAutoPage1")
	//page(name: "mainAutoPage2")
	//page(name: "nestModePresPage")
	//page(name: "schMotModePage")
	//page(name: "watchDogPage")
	//page(name: "diagnosticsPage")

	//shared pages

/*
	page(name: "setNotificationPage")
	page(name: "setNotificationPage1")
	page(name: "setNotificationPage2")
	page(name: "setNotificationPage3")
	page(name: "setNotificationPage4")
	page(name: "setNotificationPage5")
*/

/*
	page(name: "setDayModeTimePage")
	page(name: "setDayModeTimePage1")
	page(name: "setDayModeTimePage2")
	page(name: "setDayModeTimePage3")
	page(name: "setDayModeTimePage4")
	page(name: "setDayModeTimePage5")
*/
	//page(name: "setNotificationTimePage")
}

mappings {
		//Web Diagnostics Pages
		if(state?.autoTyp == "remDiag" || getDevOpt()) {
		//	path("/processCmd")     {action: [POST: "procDiagCmd"]}
			path("/diagHome")	       {action: [GET: "renderDiagHome"]}
			path("/getLogData")	     {action: [GET: "renderLogData"]}
			//path("/getLogMap")    {action: [GET: "getLogMap"]}
			path("/getManagerData") {action: [GET: "renderManagerData"]}
			path("/getAutoData")    {action: [GET: "renderAutomationData"]}
			path("/getDeviceData")  {action: [GET: "renderDeviceData"]}
		//	path("/getInstData")    {action: [GET: "renderInstData"]}
//			path("/getAppData")	     {action: [GET: "renderAppData"]}
		}
}


/******************************************************************************
|					Application Pages						|
*******************************************************************************/

def getDevOpt() {
	return parent?.getDevOpt()
}

def startPage() {
	//log.info "startPage"
	if(parent) {
		Boolean t0 = parent.getStateVal("ok2InstallAutoFlag")
		if( /* !state?.isInstalled && */ t0 != true) {
			//Logger("Not installed ${t0}")
			notAllowedPage()
		} else {
			state?.isParent = false
			selectAutoPage()
		}
	} else {
		notAllowedPage()
	}
}

def notAllowedPage () {
	dynamicPage(name: "notAllowedPage", title: "This install Method is Not Allowed", install: false, uninstall: true) {
		section() {
			paragraph imgTitle(getAppImg("disable_icon2.png"), paraTitleStr("WE HAVE A PROBLEM!\n\nDiags are enabled via App Logging: \n\nPlease use the Nest Integrations App to configure them.")), required: true, state: null
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
/*
	if(appLbl?.contains("Watchdog")) {
		if(!state?.autoTyp) { state.autoTyp = "watchDog" }
	}
*/
	if(appLbl?.contains("Diagnostics")) {
		if(!state?.autoTyp) { state.autoTyp = "remDiag" }
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
	log.debug "${app.label} Initialize..."			// Must be log.debug
	if(!state?.isInstalled) { state?.isInstalled = true }
	Boolean settingsReset = parent.getSettingVal("resetAllData")
	//if(state?.resetAllData || settingsReset) {
	//	if(fixState()) { return }	// runIn of fixState will call initAutoApp()
	//}
	runIn(6, "initAutoApp", [overwrite: true])
}

def subscriber() {

}

private adj_temp(tempF) {
	if(getTemperatureScale() == "C") {
		return ((tempF - 32) * ( 5/9 )) as Double
	} else {
		return tempF
	}
}

def setMyLockId(val) {
	if(state?.myID == null && parent && val) {
		state.myID = val
	}
}

def getMyLockId() {
	if(parent) { return state?.myID } else { return null }
}

/*
def fixState() {
	def result = false
	LogTrace("fixState")
	def before = getStateSizePerc()
	if(!state?.resetAllData && parent.getSettingVal("resetAllData")) { // automation cleanup called from update() -> initAutoApp()
		def data = getState()?.findAll { !(it?.key in [ "autoTyp", "autoDisabled", "scheduleList", "resetAllData", "autoDisabledDt",
			"leakWatRestoreMode", "leakWatTstatOffRequested",
			"conWatRestoreMode", "conWatlastMode", "conWatTstatOffRequested",
			"oldremSenTstat",
			"haveRunFan", "fanCtrlRunDt", "fanCtrlFanOffDt",
			"extTmpRestoreMode", "extTmpTstatOffRequested", "extTmpSavedTemp", "extTmplastMode", "extTmpSavedCTemp", "extTmpSavedHTemp", "extTmpChgWhileOnDt", "extTmpChgWhileOffDt",
//			"remDiagLogDataStore",
//			"restoreId", "restoredFromBackup", "restoreCompleted", "autoTypFlag", "installData", "usageMetricsStore"
 ]) }
//  "watchDogAlarmActive", "extTmpAlarmActive", "conWatAlarmActive", "leakWatAlarmActive",
		data.each { item ->
			state.remove(item?.key.toString())
		}
		setAutomationStatus()
		unschedule()
		unsubscribe()
		result = true
	} else if(state?.resetAllData && !parent.getSettingVal("resetAllData")) {
		LogAction("fixState: resetting ALL toggle", "info", true)
		state.resetAllData = false
	}

	if(result) {
		state.resetAllData = true
		LogAction("fixState: State Data: before: $before after: ${getStateSizePerc()}", "info", true)
		runIn(20, "finishFixState", [overwrite: true])
	}
	return result
}

void finishFixState(migrate=false) {
	LogTrace("finishFixState")
	if(state?.resetAllData || migrate) {
		def tstat = settings?.schMotTstat
		if(tstat) {
			LogAction("finishFixState found tstat", "info", true)
			getTstatCapabilities(tstat, schMotPrefix())
			if(!getMyLockId()) {
				setMyLockId(app.id)
			}
			if(settings?.schMotRemoteSensor) {
				LogAction("finishFixState found remote sensor", "info", true)
				if( parent?.remSenLock(tstat?.deviceNetworkId, getMyLockId()) ) {  // lock new ID
					state?.remSenTstat = tstat?.deviceNetworkId
				}
				if(isRemSenConfigured() && settings?.remSensorDay) {
					LogAction("finishFixState found remote sensor configured", "info", true)
					if(settings?.vthermostat != null) { parent?.addRemoveVthermostat(tstat.deviceNetworkId, vthermostat, getMyLockId()) }
				}
			}
		}
		if(!migrate) { initAutoApp() }
		//updated()
	}
}
*/

def selectAutoPage() {
	//LogTrace("selectAutoPage()")
	if(!state?.autoTyp) {
		return dynamicPage(name: "selectAutoPage", title: "Choose an Automation Type", uninstall: false, install: false, nextPage: null) {
/*
			def thereIsChoice = !parent.automationNestModeEnabled(null)
			if(thereIsChoice) {
				section("Set Nest Presence Based on location Modes, Presence Sensor, or Switches:") {
					href "mainAutoPage1", title: imgTitle(getAppImg("mode_automation_icon.png"), inputTitleStr("Nest Mode Automations")), description: ""//, params: ["aTyp": "nMode"]
				}
			}
			section("Thermostat Automations: Setpoints, Remote Sensor, External Temp, Humidifier, Contact Sensor, Leak Sensor, Fan Control") {
				href "mainAutoPage2", title: imgTitle(getAppImg("thermostat_automation_icon.png"), inputTitleStr("Thermostat Automations")), description: "" //, params: ["aTyp": "schMot"]
			}
*/
			notAllowedPage()
		}
	}
	else { return mainAutoPage( [aTyp: state?.autoTyp]) }
}

def sectionTitleStr(title)	{ return "<h3>$title</h3>" }
def inputTitleStr(title)	{ return "<u>$title</u>" }
def pageTitleStr(title)		{ return "<h1>$title</h1>" }
def paraTitleStr(title)		{ return "<b>$title</b>" }

//def imgTitle(imgSrc, imgWidth=30, imgHeight=null, titleStr, color=null) {
def imgTitle(imgSrc, titleStr, color=null, imgWidth=30, imgHeight=null) {
	def imgStyle = ""
	imgStyle += imgWidth ? "width: ${imgWidth}px !important;" : ""
	imgStyle += imgHeight ? "${imgWidth ? " " : ""}height: ${imgHeight}px !important;" : ""
	if(color) { return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""" }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""" }
}

// string table for titles
def titles(String name, Object... args) {
	def page_titles = [
//    "page_main": "${lname} setup and management",
//    "page_add_new_cid_confirm": "Add new CID switch : %s",
//    "input_selected_devices": "Select device(s) (%s found)",
		"t_dtse": "Delay to set ECO (in Minutes)",
		"t_dr": "Delay Restore (in Minutes)",
		"t_ca": "Configured Alerts",
		"t_cr": "Configured Restrictions",
		"t_nt": "Notifications:",
		"t_nlw": "Nest Location Watchdog"
	]
	if (args)
		return String.format(page_titles[name], args)
	else
		return page_titles[name]
}

// string table for descriptions
def descriptions(name, Object... args) {
	def element_descriptions = [
		"d_ttc": "Tap to configure",
		"d_ttm": "\n\nTap to modify"
	]
	if (args)
		return String.format(element_descriptions[name],args)
	else
		return element_descriptions[name]
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

def getAppImg(imgName, on = null) {
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/App/$imgName" : ""
	return (!disAppIcons || on) ? icons(imgName) : ""
}

def getDevImg(imgName, on = null) {
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/Devices/$imgName" : ""
	return (!disAppIcons || on) ? icons(imgName, "Devices") : ""
}

/*
def mainAutoPage1(params) {
	//LogTrace("mainAutoPage1()")
	def t0 = [:]
	t0.aTyp = "nMode"
	return mainAutoPage( t0 ) //[autoType: "nMode"])
}

def mainAutoPage2(params) {
	//LogTrace("mainAutoPage2()")
	def t0 = [:]
	t0.aTyp = "schMot"
	return mainAutoPage( t0 ) //[autoType: "schMot"])
}
*/
def mainAutoPage(params) {
	//LogTrace("mainAutoPage()")
	def t0 = getTemperatureScale()?.toString()
	state?.tempUnit = (t0 != null) ? t0 : state?.tempUnit
	if(!state?.autoDisabled) { state.autoDisabled = false }
	def autoType = null
	//If params.aTyp is not null then save to state.
	if(!state?.autoTyp) {
		if(!params?.aTyp) { Logger("nothing is set mainAutoPage") }
		else {
			//Logger("setting autoTyp")
			state.autoTyp = params?.aTyp
			autoType = params?.aTyp;
		}
	} else {
		//Logger("setting autoTyp")
		autoType = state.autoTyp
	}

	//Logger("mainPage: ${state.autoTyp}  ${autoType}")
	// If the selected automation has not been configured take directly to the config page.  Else show main page
//Logger("in mainAutoPage ${autoType}  ${state?.autoTyp}")
/*
	if(autoType == "nMode" && !isNestModesConfigured())		{ return nestModePresPage() }
	else if(autoType == "watchDog" && !isWatchdogConfigured())	{ return watchDogPage() }
	else if(autoType == "schMot" && !isSchMotConfigured())		{ return schMotModePage() }
	else
*/
	//if(autoType == "remDiag" && !isDiagnosticsConfigured())    { return diagnosticsPage() }

//	else {
		//Logger("in main page")
		// Main Page Entries
		//return dynamicPage(name: "mainAutoPage", title: "Automation Configuration", uninstall: false, install: false, nextPage: "nameAutoPage" ) {
		return dynamicPage(name: "mainAutoPage", title: pageTitleStr("Automation Configuration"), uninstall: true, install: true, nextPage:null ) {
			section() {
				if(settings?.autoDisabledreq) {
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is currently disabled!\nTurn it back on to to make changes or resume operation")), required: true, state: null
				} else {
					if(getIsAutomationDisabled()) { paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is still disabled!\nPress Next and Done to Activate this Automation Again")), state: "complete" }
				}
				if(!getIsAutomationDisabled()) {
					if(autoType == "remDiag") {
						def diagDesc = ""
						def t1 = parent.getSettingVal("enDiagWebPage")
						diagDesc += " • Web page enabled"
						t1 = parent.getSettingVal("enRemDiagLogging")
						diagDesc += t1 ? "\n • Web Logs enabled" : ""
						def remDiagDesc = isDiagnosticsConfigured() ? "${diagDesc}" : null
						if(remDiagDesc) {
							paragraph sectionTitleStr(remDiagDesc)
						//href "diagnosticsPage", title: imgTitle(getAppImg("diag_icon.png"), inputTitleStr("NST Diagnostics")), description: remDiagDesc ?: descriptions("d_ttc"), state: (remDiagDesc ? "complete" : null)

							if(!state?.access_token) { getAccessToken() }
							if(!state?.access_token) { enableOauth(); getAccessToken() }

							def myUrl = getAppEndpointUrl("diagHome")
							def myStr = """ <a href="${myUrl}" target="_blank">NST Diagnostic Web Page</a> """
// web_icon.png ?
							paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
						}
					}
				}
			}

			section(sectionTitleStr("Automation Options:")) {
				if( /* (isNestModesConfigured() || isWatchdogConfigured() || isSchMotConfigured())*/ isDiagnosticsConfigured() ) {
					//paragraph paraTitleStr("Enable/Disable this Automation")
					input "autoDisabledreq", "bool", title: imgTitle(getAppImg("disable_icon2.png"), inputTitleStr("Disable this Automation?")), required: false, defaultValue: false /* state?.autoDisabled */, submitOnChange: true
					setAutomationStatus()
				}
				input ("showDebug", "bool", title: imgTitle(getAppImg("debug_icon.png"), inputTitleStr("Debug Option")), description: "Show ${app?.name} Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true)
				if(showDebug) {
					input (name: "advAppDebug", type: "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose Logs?")), required: false, defaultValue: false, submitOnChange: true)
				} else {
					settingUpdate("advAppDebug", "false", "bool")
				}
			}
			section(paraTitleStr("Automation Name:")) {
				def newName = getAutoTypeLabel()
				if(!app?.label) { app?.updateLabel("${newName}") }
				label title: imgTitle(getAppImg("name_tag_icon.png"), inputTitleStr("Label this Automation: Suggested Name: ${newName}")), defaultValue: "${newName}", required: true //, wordWrap: true
				if(!state?.isInstalled) {
					paragraph "Make sure to name it something that you can easily recognize."
				}
			}
		}
//	}
}

/*
def getSchMotConfigDesc(retAsList=false) {
	def list = []
	if(settings?.schMotWaterOff) { list.push("Turn Off if Leak Detected") }
	if(settings?.schMotContactOff) { list.push("Set ECO if Contact Open") }
	if(settings?.schMotExternalTempOff) { list.push("Set ECO based on External Temp") }
	if(settings?.schMotRemoteSensor) { list.push("Use Remote Temp Sensors") }
	if(isTstatSchedConfigured()) { list.push("Setpoint Schedules Created") }
	if(settings?.schMotOperateFan) { list.push("Control Fans with HVAC") }
	if(settings?.schMotHumidityControl) { list.push("Control Humidifier") }

	if(retAsList) {
		return isSchMotConfigured() ? list : null
	} else {
		def sDesc = ""
		sDesc += settings?.schMotTstat ? "${settings?.schMotTstat?.label}" : ""
		list?.each { ls ->
			sDesc += "\n • ${ls}"
		}
		def t1 = getNotifConfigDesc("schMot")
		sDesc += t1 ? "\n\n${t1}" : ""
		sDesc += settings?.schMotTstat ? descriptions("d_ttm") : ""
		return isSchMotConfigured() ? "${sDesc}" : null
	}
}
*/

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

def initAutoApp() {
	//log.debug "${app.label} initAutoApp..."			// Must be log.debug
	if(settings["watchDogFlag"]) {
		state?.autoTyp = "watchDog"
	}

	def autoType = getAutoType()
/*
	if(autoType == "nMode") {
		parent.automationNestModeEnabled(true)
	} else
*/
	if(settings["remDiagFlag"] || state?.autoTyp == "remDiag") {
		//state?.automationType = "remDiag"
		parent?.remDiagAppAvail(true)
	}

	unschedule()
	unsubscribe()
	//def autoDisabled = getIsAutomationDisabled()
	setAutomationStatus()

	automationsInst()

	subscribeToEvents()
	scheduler()

	app.updateLabel(getAutoTypeLabel())
	LogAction("Automation Label: ${getAutoTypeLabel()}", "info", false)

	//stateRemove("motionnullLastisBtwn")

	stateRemove("evalSched")
	stateRemove("dbgAppndName")   // cause Automations to re-check with parent for value
	//stateRemove("wDevInst")   // cause Automations to re-check with parent for value after updated is called
	stateRemove("enRemDiagLogging")   // cause Automations to re-check with parent for value after updated is called

	settingUpdate("showDebug", "true",  "bool")
	settingUpdate("advAppDebug", "true", "bool")

	scheduleAutomationEval(30)
	if(settings?.showDebug || settings?.advAppDebug) { runIn(1800, logsOff) }

}

def logsOff() {
	log.warn "${app.label} debug logging disabled..."
	settingUpdate("showDebug", "false",  "bool")
	settingUpdate("advAppDebug", "false", "bool")
}

def uninstAutomationApp() {
	//LogTrace("uninstAutomationApp")
	def autoType = getAutoType()

	if(autoType == "remDiag") {
		parent?.remDiagAppAvail(false)
	}
}

def getCurAppLbl() { return app?.label?.toString() }

def getAutoTypeLabel() {
	//LogTrace("getAutoTypeLabel()")
	def type = state?.autoTyp
	def appLbl = getCurAppLbl()
	def newName = appName() == "${appLabel()}" ? "NST Diagnostics" : "${appName()}"
	def typeLabel = ""
	def newLbl
	def dis = (getIsAutomationDisabled() == true) ? "\n(Disabled)" : ""

	if(type == "remDiag")      { typeLabel = "NST Diagnostics"}

//log.info "getAutoTypeLabel: ${type} ${appLbl}  ${appName()} ${appLabel()} ${typeLabel}"

	if(appLbl != "" && appLbl && appLbl != "Nest Manager" && appLbl != "${appLabel()}") {
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

def getSettingsData() {
	def sets = []
	settings?.sort().each { st ->
		sets << st
	}
	return sets
}

def getSettingVal(var) {
	return settings[var] ?: null
}

def getStateVal(var) {
	return state[var] ?: null
}

public automationsInst() {
	state?.isInstalled = true
}

List getAutomationsInstalled() {
	List list = []
	String aType = state?.autoTyp
	switch(aType) {
		case "remDiag":
			list.push(aType)
			break
	}
	//LogTrace("getAutomationsInstalled List: $list")
	return list
}

String getAutomationType() {
	return state?.autoTyp ?: null
}

String getAutoType() { return !parent ? "" : state?.autoTyp }

def getIsAutomationDisabled() {
	def dis = state?.autoDisabled
	return (dis != null && dis == true) ? true : false
}

def subscribeToEvents() {
	//Remote Sensor Subscriptions
	String autoType = getAutoType()
	List swlist = []

	//remDiag Subscriptions
	if(autoType == "remDiag") {

	}
}

def scheduler() {
	def random = new Random()
	def random_int = random.nextInt(60)
	def random_dint = random.nextInt(9)

	def autoType = getAutoType()
/*
	if(autoType == "schMot" && state?.scheduleActiveCnt && state?.scheduleTimersActive) {
		LogTrace("${autoType} scheduled (${random_int} ${random_dint}/5 * * * ?)")
		schedule("${random_int} ${random_dint}/5 * * * ?", heartbeatAutomation)
	} else
*/
	if(autoType != "remDiag" && autoType != "storage") {
		LogTrace("${autoType} scheduled (${random_int} ${random_dint}/30 * * * ?)")
		schedule("${random_int} ${random_dint}/30 * * * ?", heartbeatAutomation)
	}
}

def heartbeatAutomation() {
	def autoType = getAutoType()
	def str = "heartbeatAutomation() ${autoType}"
	def val = 900
	if(autoType == "schMot") {
		val = 220
	}
	if(getAutoRunInSec() > val) {
		LogTrace("${str} RUN")
		runAutomationEval()
	} else {
		LogTrace("${str} NOT NEEDED")
	}
}

def defaultAutomationTime() {
	return 20
}

def scheduleAutomationEval(schedtime = defaultAutomationTime()) {
	def theTime = schedtime
	if(theTime < defaultAutomationTime()) { theTime = defaultAutomationTime() }
	def autoType = getAutoType()
	def random = new Random()
	def random_int = random.nextInt(6)  // this randomizes a bunch of automations firing at same time off same event
	def waitOverride = false
	switch(autoType) {
		case "watchDog":
			if(theTime == defaultAutomationTime()) {
				theTime = 35 + random_int  // this has watchdog fire last so other automations can finish changes
			}
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

def getAutoRunInSec() { return !state?.autoRunInSchedDt ? 100000 : GetTimeDiffSeconds(state?.autoRunInSchedDt, null, "getAutoRunInSec").toInteger() }

def runAutomationEval() {
	LogTrace("runAutomationEval")
	def autoType = getAutoType()
	state.evalSched = false
	switch(autoType) {
		case "remDiag":
			if(isDiagnosticsConfigured()) {
				//remDiagCheck()
			}
			break
		default:
			LogAction("runAutomationEval: Invalid Option Received ${autoType}", "warn", true)
			break
	}
}


def storeLastAction(actionDesc, actionDt, autoType=null, dev=null) {
	if(actionDesc && actionDt) {

		def newVal = ["actionDesc":actionDesc, "dt":actionDt, "autoType":autoType]
		state?.lastAutoActionData = newVal

		def list = state?.detailActionHistory ?: []
		def listSize = 30
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
		if(list) { state?.detailActionHistory = list }

//		if(dev) {
//			sendAutoChgToDevice(dev, autoType, actionDesc)		// THIS ONLY WORKS ON NEST THERMOSTATS
//		}
	}
}


def automationGenericEvt(evt) {
	def startTime = now()
	def eventDelay = startTime - evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "info", false)

// if streaming, this is not needed
//	if(isRemSenConfigured() && settings?.vthermostat) {
//		state.needChildUpdate = true
//	}
//	if(settings?.humCtrlUseWeather && isHumCtrlConfigured()) {
//		state.needWeathUpd = true
//	}

	doTheEvent(evt)
}

def doTheEvent(evt) {
	if(getIsAutomationDisabled()) { return }
	else {
		scheduleAutomationEval()
		storeLastEventData(evt)
	}
}

/******************************************************************************
|					       REMOTE DIAG AUTOMATION LOGIC CODE					       |
*******************************************************************************/
def remDiagPrefix() { return "remDiag" }

def isDiagnosticsConfigured() {
	def t0 = parent.getSettingVal("enDiagWebPage")
	//def t1 = parent.getSettingVal("enRemDiagLogging")
	return (state?.autoTyp == "remDiag" && t0) ? true : false
	//return (state?.automationType == "remDiag") ? true : false
}

def savetoRemDiagChild(List newdata) {
	//LogTrace("savetoRemDiagChild($msg, $type, $logSrcType)")
	if(state?.autoTyp == "remDiag") {
		def stateSz = getStateSizePerc()
		if(stateSz >= 75) {
			// this is log.xxxx to avoid looping/recursion
			log.warn "savetoRemDiagChild: log storage trimming state size is ${getStateSizePerc()}%"
		}
		if(newdata?.size() > 0) {
			def data = atomicState?.remDiagLogDataStore ?: []
			def pdata = atomicState?.remDiagLogpDataStore ?: []
			def cnt = 0
			while(data && stateSz >= 60 && cnt < 50) {
				data.remove(0)
				atomicState?.remDiagLogDataStore = data
				stateSz = getStateSizePerc()
				cnt += 1
			}
			newdata?.each { logItem ->
				pdata << logItem
				data << logItem
				cnt -= 1
				//log.debug "item: $logItem"
				//def item = ["dt":getDtNow(), "type":type, "src":(logSrcType ?: "Not Set"), "msg":msg]
			}
			atomicState?.remDiagLogDataStore = data
			atomicState?.remDiagLogpDataStore = pdata
			stateSz = getStateSizePerc()
			while(data && stateSz >= 75 && cnt < 50) {
				data.remove(0)
				atomicState?.remDiagLogDataStore = data
				stateSz = getStateSizePerc()
				cnt += 1
			}
			runIn(3, "displayLogData", [overwrite: true])
			log.debug "Log Items (${data?.size()}) | State Size: (${stateSz}%)"
		} else { log.error "bad call to savetoRemDiagChild - no data" }
	} else { Logger("bad call to savetoRemDiagChild - wrong automation") }
}

def displayLogData() {
		def logData = atomicState?.remDiagLogpDataStore ?: []
		def logSz = logData?.size() ?: 0
		def cnt = 1
		if(logSz > 0) {
			logData?.sort { it?.dt }.reverse()?.each { logItem ->
				switch(logItem?.type) {
					case "info":
						log.info "${logItem?.src} ${logItem.msg}"
						break
					case "warn":
						log.warn "${logItem?.src} ${logItem.msg}"
						break
					case "error":
						log.error "${logItem?.src} ${logItem.msg}"
						break
					case "trace":
						log.trace "${logItem?.src} ${logItem.msg}"
						break
					case "debug":
						log.debug "${logItem?.src} ${logItem.msg}"
						break
					default:
						log.debug "${logItem?.src} ${logItem.msg}"
						break
				}
			}
			atomicState?.remDiagLogpDataStore = []
		}
}

def getRemLogData() {
	try {
		def appHtml = ""
		def navHtml = ""
		def scrStr = ""
		def logData = atomicState?.remDiagLogDataStore
		def homeUrl = getAppEndpointUrl("diagHome")
		def resultStr = ""
		def tf = new SimpleDateFormat("h:mm:ss a")
		tf.setTimeZone(getTimeZone())
		def logSz = logData?.size() ?: 0
		def cnt = 1
		// def navMap = [:]
		// navMap = ["key":cApp?.getLabel(), "items":["Settings", "State", "MetaData"]]
		// def navItems = navHtmlBuilder(navMap, appNum)
		// if(navItems?.html) { navHtml += navItems?.html }
		// if(navItems?.js) { scrStr += navItems?.js }
		if(logSz > 0) {
			logData?.sort { it?.dt }.reverse()?.each { logItem ->
				def tCls = ""
				switch(logItem?.type) {
					case "info":
						tCls = "label-info"
						break
					case "warn":
						tCls = "label-warning"
						break
					case "error":
						tCls = "label-danger"
						break
					case "trace":
						tCls = "label-default"
						break
					case "debug":
						tCls = "label-primary"
						break
					default:
						tCls = "label-primary"
						break
				}
				def srcCls = "defsrc-bg"
				if(logItem?.src.toString().startsWith("Manager")) {
					srcCls = "mansrc-bg"
				} else if(logItem?.src.toString().startsWith("Camera")) {
					srcCls = "camsrc-bg"
				} else if(logItem?.src.toString().startsWith("Protect")) {
					srcCls = "protsrc-bg"
				} else if(logItem?.src.toString().startsWith("Thermostat")) {
					srcCls = "tstatsrc-bg"
				} else if(logItem?.src.toString().startsWith("weather")) {
					srcCls = "weatsrc-bg"
				} else if(logItem?.src.toString().startsWith("Presence")) {
					srcCls = "pressrc-bg"
				} else if(logItem?.src.toString().startsWith("Automation")) {
					srcCls = "autosrc-bg"
				}
				resultStr += """
					${cnt > 1 ? "<br>" : ""}
					<div class="log-line">
						<span class="log-time">${tf?.format(logItem?.dt)}</span>:
						<span class="log-type $tCls">${logItem?.type}</span> |
						<span class="log-source ${srcCls}"> ${logItem?.src}</span>:
						<span class="log-msg"> ${logItem?.msg}</span>
					</div>
				"""
				cnt = cnt+1
			}
		} else {
			resultStr = "There are NO log entries available."
		}

		return """
			<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
				<meta name="description" content="NST - Logs">
				<meta name="author" content="Anthony S.">
				<meta http-equiv="cleartype" content="on">
				<meta name="MobileOptimized" content="320">
				<meta name="HandheldFriendly" content="True">
				<meta name="apple-mobile-web-app-capable" content="yes">

				<title>NST Diagnostics - Logs</title>

				<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
				<link href="https://fonts.googleapis.com/css?family=Roboto" rel="stylesheet">
				<script src="https://use.fontawesome.com/fbe6a4efc7.js"></script>
				<script src="https://fastcdn.org/FlowType.JS/1.1/flowtype.js"></script>
				<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/normalize/7.0.0/normalize.min.css">
				<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css" integrity="sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u" crossorigin="anonymous">
				<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css" integrity="sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp" crossorigin="anonymous">
				<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/hamburgers/0.9.1/hamburgers.min.css">
				<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js" integrity="sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa" crossorigin="anonymous"></script>
				<script src="https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/1.7.1/clipboard.min.js"></script>
				<link rel="stylesheet" href="https://rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages.min.css">
				<style>
				.pushy {
					position: fixed;
					width: 250px;
					height: 100%;
					top: 0;
					z-index: 9999;
					background: #191918;
					opacity: 0.6;
					overflow: auto;
					-webkit-overflow-scrolling: touch;
					/* enables momentum scrolling in iOS overflow elements */
				}
				.nav-home-btn {
					padding: 20px 10px 0 10px;
					font-size: 22px;
					-webkit-text-stroke: white;
					-webkit-text-stroke-width: thin;
				}
				.right-head-col {
					padding: 2em 40px 0 0;
				}
				.hamburger-box {
					width: 25px;
					height: 24px;
				}

				.hamburger-inner, .hamburger-inner:after, .hamburger-inner:before {
					width: 25px;
					height: 4px;
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
					<!--Page Header Section -->
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> Logs</img></h3>
									<h6 style="font-size: 0.9em;">This Includes Automations, Device, Manager Logs</h6>
								</div>
								<div class="col-xs-2 right-head-col">
									<button id="rfrshBtn" type="button" class="btn refresh-btn pull-right" title="Refresh Page Content"><i id="rfrshBtnIcn" class="fa fa-refresh" aria-hidden="true"></i></button>
								</div>
							</div>
						</div>
					</div>
					<!-- Page Content -->
					<div id="page-content-wrapper">
						<div class="container">
						   <!--First Panel Section -->
						   <div id="main" class="panel-body">
								<div class="panel panel-primary">
									<div class="panel-heading">
										<div class="row">
											<div class="col-xs-10" style="padding-left: 25px;">
												<div class="row">
													<h1 class="panel-title pnl-head-title pull-left">Log Stream</h1>
												</div>
												<div class="row">
													<small class="pull-left" style="text-decoration: underline;">${logSz} Items</small>
												</div>
											</div>
											<div class="col-xs-2" style="padding: 10px;">
												<button id="exportLogPdfBtn" type="button" title="Export Content as PDF" class="btn export-pdf-btn pull-right"><i id="exportPdfBtnIcn" class="fa fa-file-pdf-o" aria-hidden="true"></i> PDF</button>
											</div>
										</div>
									</div>
									<div class="panel-body" style="background-color: #DEDEDE;">
										<div id="logBody" class="logs-div">
											<div>${resultStr}</div>
									</div>
									</div>
								</div>
							</div>
						</div>
					</div>
				</div>
				<script src="https://rawgit.com/tonesto7/nest-manager/master/Documents/js/diagpages.min.js"></script>
				<script>
					\$("#goHomeBtn").click(function() {
						closeNavMenu();
						toggleMenuBtn();
						window.location.replace('${homeUrl}');
					});
				</script>
			</body>
		"""
/* """ */
	}  catch (ex) { log.error "getRemLogData Exception:", ex }
	return null
}


def getAccessToken() {
	try {
		if(!state?.access_token) { state?.access_token = createAccessToken() }
		else { return true }
	}
	catch (ex) {
		def msg = "Error: OAuth is not Enabled for ${app?.name}!."
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


def renderLogData() {
	try {
		//def remDiagApp = getRemDiagApp()
		def resultStr = "There are no logs to show... Is logging turned on?"

		//def logData = remDiagApp?.getRemLogData()
		def logData = getRemLogData()
		if(logData) {
			resultStr = logData
		}
		render contentType: "text/html", data: resultStr
	} catch (ex) { log.error "renderLogData Exception:", ex }
}

//def getDiagHomeUrl() { getAppEndpointUrl("diagHome") }

def getAppEndpointUrl(subPath) { return "${getFullApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }
def getLocalEndpointUrl(subPath) { return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }

/*
def getLogMap() {
	try {
		def remDiagApp = getRemDiagApp()
		def resultJson = new groovy.json.JsonOutput().toJson(remDiagApp?.getStateVal("remDiagLogDataStore"))
		render contentType: "application/json", data: resultJson
	} catch (ex) { log.error "getLogMap Exception:", ex }
}
*/

def lastCmdDesc() {
	def cmdDesc = ""
	def map = [:]
	def t0 =  parent.getTimestampVal("lastCmdSentDt")
	map["DateTime"] = t0 ?: "Nothing found"
	t0 = parent.getStateVal("lastCmdSent")
	map["Cmd Sent"] = t0 ?: "Nothing found"
	t0 = parent.getStateVal("lastCmdSentStatus")
	map["Cmd Result"] = t0 ? "(${t0})" : "(Nothing found)"
	cmdDesc += getMapDescStr(map)
	return cmdDesc
}


def getWebHeaderHtml(title, clipboard=true, vex=false, swiper=false, charts=false) {
	def html = """
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
		<meta name="description" content="NST Diagnostics">
		<meta name="author" content="Anthony S.">
		<meta http-equiv="cleartype" content="on">
		<meta name="MobileOptimized" content="320">
		<meta name="HandheldFriendly" content="True">
		<meta name="apple-mobile-web-app-capable" content="yes">

		<title>NST Diagnostics - ${title}</title>

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
			const serverUrl = '${apiServerUrl('')}';
			const cmdUrl = '${getAppEndpointUrl('processCmd')}';
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

def renderDiagHome() {
	try {
		def remDiagUrl = getAppEndpointUrl("diagHome")
		def logUrl = getAppEndpointUrl("getLogData")
		def managerUrl = getAppEndpointUrl("getManagerData")
		def autoUrl = getAppEndpointUrl("getAutoData")
		def deviceUrl = getAppEndpointUrl("getDeviceData")
		def appDataUrl = getAppEndpointUrl("getAppData")
		def instDataUrl // = getAppEndpointUrl("getInstData")
		def devTilesUrl = getAppEndpointUrl("deviceTiles")
		def tstatTilesUrl = getAppEndpointUrl("tstatTiles")
		def protTilesUrl = getAppEndpointUrl("protectTiles")
		def camTilesUrl = getAppEndpointUrl("cameraTiles")
		def weatherTilesUrl // = getAppEndpointUrl("weatherTile")
		def t0 = parent.getStateSizePerc()
		def sPerc = t0 ?: 0
		def instData //= atomicState?.installData
		def cmdDesc = lastCmdDesc().toString().replaceAll("\n", "<br>")
		//def newHtml = getWebData([uri: "https://raw.githubusercontent.com/${gitPath()}/Documents/html/diagHome.html", contentType: "text/plain; charset=UTF-8"], "newHtml").toString()
		//log.debug "newHtml: $newHtml"
		def tit = "(${parent.getStateVal("structureName")}) Location"
		def html = """
			<head>
				${getWebHeaderHtml(tit)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/toubou91/percircle/master/dist/css/percircle.css">
				<script src="https://cdn.rawgit.com/toubou91/percircle/master/dist/js/percircle.js"></script>
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diaghome.min.css">
				<style>
				</style>
			</head>
			<body>
				<button onclick="topFunction()" id="scrollTopBtn" title="Go to top"><i class="fa fa-arrow-up centerText" aria-hidden="true"></i></button>

				<!-- Your Content -->
				<div id="container">
					<div id="top-hdr" class="navbar navbar-default navbar-fixed-top">
						<div class="centerText">
							<div class="row">
								<div class="col-xs-2"></div>
								<div class="col-xs-8 centerText">
									<h4 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> Diagnostics Home ${tit}</img></h4>
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
							<!--First Panel Section -->
							<div class="panel panel-primary">
								<!--First Panel Section Heading-->
								<div class="panel-heading">
									<div class="row">
										<div class="col-xs-12">
											<h1 class="panel-title panel-title-text">Install Details:</h1>
										</div>
									</div>
								</div>

								<!--First Panel Section Body -->
								<div class="panel-body" style="overflow-y: auto;">
									<div class="container-fluid">
										<!--First Panel Section Body Row 1-->
										<div class="row" style="min-height: 100px;">

											<!--First Panel Section Body Row 1 - Col1 -->
											<div class=" col-xs-12 col-sm-8">
												<div id="instContDiv" style="padding: 0 10px;">
													<div class="row panel-border centerText">
														<div class="col-xs-12 col-sm-6 install-content">
															<span><b>Version:</b></br><small>${appVersion()}</small></span>
														</div>
														<div class="col-xs-12 col-sm-6 install-content">
															<span><b>Install ID:</b></br><small>${atomicState?.installationId}</small></span>
														</div>

													<div class="col-xs-12 col-sm-6 install-content">
														<span><b>Install Date:</b></br><small>${instData?.dt}</small></span>
													</div>
													<div class="col-xs-12 col-sm-6 install-content">
														<span><b>Last Updated:</b></br><small>${instData?.updatedDt}</small></span>
													</div>
													<div class="col-xs-12 col-sm-6 install-content">
														<span><b>Init. Version:</b></br><small>${instData?.initVer}</small></span>
													</div>
													<div class="col-xs-12 col-sm-6 install-content">
														<span><b>Fresh Install:</b></br><small>${instData?.freshInstall}</small></span>
													</div>
												</div>
											</div>
										</div>
										<!--First Panel Section Body Row 1 - Col2 -->
										<div class="col-xs-12 col-sm-4" style="padding: 25px;">
												<div style="pull-right">
													<div class="stateUseTitleText">State Usage</div>
													<div id="stateUseCirc" data-percent="${sPerc}" data-text="<p class='stateUseCircText'>${sPerc}%</p>" class="small blue2 center"></div>
												</div>
											</div>
										</div>
										<hr/>
										<!--First Panel Section Body Row 2 -->
										<div class="row" style="min-height: 100px;">
											<!--First Panel Section Body Row 2 - Col 1 -->
											<div id="instContDiv" style="padding: 0 10px;">
												<div class="panel panel-default">
													<div id="item${appNum}-settings" class="panel-heading">
														<h1 class="panel-title subpanel-title-text">Last Command Info:</h1>
													</div>
													<div class="panel-body">
														<div><pre class="mapDataFmt">${lastCmdDesc().toString().replaceAll("\n", "<br>")}</pre></div>
													</div>
												</div>
											</div>
										</div>
									</div>
								</div>
							</div>

							<!--Second Panel Section -->
							<div class="panel panel-info">
								<div class="panel-heading">
									<h1 class="panel-title">Shortcuts</h1>
								</div>
								<div class="panel-body">
									<div class="col-xs-6 centerText">
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${logUrl}" role="button">View Logs</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${managerUrl}" role="button">Manager Data</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${autoUrl}" role="button">Automation Data</a></p>
									</div>
									<div class="col-xs-6 centerText">
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${deviceUrl}" role="button">Device Data</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${instDataUrl}" role="button">Install Data</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${appDataUrl}" role="button">AppData File</a></p>
									</div>
								</div>
							</div>
							<!--Third Panel Section -->
							<div class="panel panel-warning">
								<div class="panel-heading">
									<h1 class="panel-title">Diagnostic Commands</h1>
								</div>
								<div class="panel-body">
									<div class="col-xs-6 centerText">
										<p><a class="btn btn-primary btn-md shortcutBtns" id="updateMethodBtn" role="button">Run Update()</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" id="stateCleanupBtn" role="button">Run StateCleanup()</a></p>
										<p><a class="btn btn-primary btn-md shortcutBtns" id="sendInstallDataBtn" role="button">Run SendInstallData()</a></p>
									</div>
								</div>
							</div>
							<!--Fourth Panel Section -->
							<div class="panel panel-success">
								<div class="panel-heading">
									<h1 class="panel-title">Device Tiles</h1>
								</div>
								<div class="panel-body">
									<div class="col-xs-6 centerText">
										<p><a class="btn btn-primary btn-md shortcutBtns" href="${devTilesUrl}" role="button">All Devices</a></p>
										${atomicState?.thermostats ? """<p><a class="btn btn-primary btn-md shortcutBtns" href="${tstatTilesUrl}" role="button">Thermostat Devices</a></p>""" : ""}
										${atomicState?.protects ? """<p><a class="btn btn-primary btn-md shortcutBtns" href="${protTilesUrl}" role="button">Protect Devices</a></p>""" : ""}
									</div>
									<div class="col-xs-6 centerText">
										${atomicState?.cameras ? """<p><a class="btn btn-primary btn-md shortcutBtns" href="${camTilesUrl}" role="button">Camera Devices</a></p>""" : ""}
										${atomicState?.weatherDevice ? """<p><a class="btn btn-primary btn-md shortcutBtns" href="${weatherTilesUrl}" role="button">Weather Device</a></p>""" : ""}
									</div>
								</div>
							</div>
							<footer class="footer">
								<div class="container">
									<div class="well well-sm footerText">
										<span>External Access URL: <button id="copyUrlBtn" class="btn" title="Copy URL to Clipboard" type="button" data-clipboard-action="copy" data-clipboard-text="${remDiagUrl}"><i class="fa fa-clipboard" aria-hidden="true"></i></button></span>
									</div>
								</div>
							</footer>
						</div>
					</div>
				</div>
				<script src="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/js/diaghome.min.js"></script>
			</body>
		"""
/* """ */
		render contentType: "text/html", data: html
	} catch (ex) { log.error "renderDiagUrl Exception:", ex }
}

def dumpListDesc(data, level, List lastLevel, listLabel, html=false) {
	def str = ""
	def cnt = 1
	def newLevel = lastLevel

	def list1 = data?.collect {it}
	list1?.each { par ->
		def t0 = cnt - 1
		if(par instanceof Map) {
			def newmap = [:]
			newmap["${listLabel}[${t0}]"] = par
			def t1 = (cnt == list1.size()) ? true : false
			newLevel[level] = t1
			str += dumpMapDesc(newmap, level, newLevel, !t1)
		} else if(par instanceof List || par instanceof ArrayList) {
			def newmap = [:]
			newmap["${listLabel}[${t0}]"] = par
			def t1 = (cnt == list1.size()) ? true : false
			newLevel[level] = t1
			str += dumpMapDesc(newmap, level, newLevel, !t1)
		} else {
			def lineStrt = "\n"
			for(int i=0; i < level; i++) {
				lineStrt += (i+1 < level) ? (!lastLevel[i] ? "   │" : "    " ) : "   "
			}
			lineStrt += (cnt == 1 && list1.size() > 1) ? "┌── " : (cnt < list1?.size() ? "├── " : "└── ")
			str += "${lineStrt}${listLabel}[${t0}]: ${par} (${getObjType(par)})"
		}
		cnt = cnt+1
	}
	return str
}

def dumpMapDesc(data, level, List lastLevel, listCall=false, html=false) {
	def str = ""
	def cnt = 1
	data?.sort()?.each { par ->
		def lineStrt = ""
		def newLevel = lastLevel
		def thisIsLast = (cnt == data?.size() && !listCall) ? true : false
		if(level > 0) {
			newLevel[(level-1)] = thisIsLast
		}
		def theLast = thisIsLast
		if(level == 0) {
			lineStrt = "\n\n • "
		} else {
			theLast == (last && thisIsLast) ? true : false
			lineStrt = "\n"
			for(int i=0; i < level; i++) {
				lineStrt += (i+1 < level) ? (!newLevel[i] ? "   │" : "    " ) : "   "
			}
			lineStrt += ((cnt < data?.size() || listCall) && !thisIsLast) ? "├── " : "└── "
		}
		if(par?.value instanceof Map) {
			str += "${lineStrt}${par?.key.toString()}: (Map)"
			newLevel[(level+1)] = theLast
			str += dumpMapDesc(par?.value, level+1, newLevel)
		}
		else if(par?.value instanceof List || par?.value instanceof ArrayList) {
			str += "${lineStrt}${par?.key.toString()}: [List]"
			newLevel[(level+1)] = theLast

			str += dumpListDesc(par?.value, level+1, newLevel, "") //par?.key.toString())
		}
		else {
			def objType = getObjType(par?.value)
			if(html) {
				def cls = mapDescValHtmlCls(par?.value)
				str += "<span>${lineStrt}${par?.key.toString()}: (${par?.value}) (${objType})</span>"
			} else {
				str += "${lineStrt}${par?.key.toString()}: (${par?.value}) (${objType})"
			}
		}
		cnt = cnt + 1
	}
	return str
}

def mapDescValHtmlCls(value) {
	if(!value) { return "" }
}

def getMapDescStr(data) {
	def str = ""
	def lastLevel = [true]
	str = dumpMapDesc(data, 0, lastLevel)
	//log.debug "str: $str"
	return str != "" ? str : "No Data was returned"
}

def renderManagerData() {
	try {
		def appHtml = ""
		def navHtml = ""
		def scrStr = ""
		def appNum = 1
		def setDesc = getMapDescStr(parent.getSettingVal(null))
		def noShow = ["authToken", "accessToken" ]
		def stData = parent.getState()?.sort()?.findAll { !(it.key in noShow) }
		def stateData = [:]
		stData?.sort().each { item ->
			stateData[item?.key] = item?.value
		}

		def navMap = [:]
		//navMap = ["key":parent.getLabel(), "items":["Settings", "State", "MetaData"]]
		navMap = ["key":parent.label, "items":["Settings", "State"]]
		def navItems = navHtmlBuilder(navMap, appNum)
		if(navItems?.html) { navHtml += navItems?.html }
		if(navItems?.js) { scrStr += navItems?.js }
		def stateDesc = getMapDescStr(stateData)
		//def metaDesc = getMapDescStr(parent.getMetadata())
		def tit = "(${parent.getStateVal("structureName")}) Manager Data"
		def html = """
			<head>
				${getWebHeaderHtml(tit)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages.min.css">
				<style>
					.pushy {
					    position: fixed;
					    width: 250px;
					    height: 100%;
					    top: 0;
					    z-index: 9999;
					    background: #191918;
					    opacity: 0.6;
					    overflow: auto;
					    -webkit-overflow-scrolling: touch;
					    /* enables momentum scrolling in iOS overflow elements */
					}
					.nav-home-btn {
					    padding: 20px 10px 0 10px;
					    font-size: 22px;
					    -webkit-text-stroke: white;
					    -webkit-text-stroke-width: thin;
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> ${tit}</img></h3>
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
							<!--First Panel Section -->
							<div id="main" class="panel-body">
								<div id="key-item1" class="panel panel-primary">
									<div class="panel-heading">
										<div class="row">
										<div class="col-xs-10">
											<h1 class="panel-title panel-title-text">NST Manager:</h1>
										</div>
										<div class="col-xs-2" style="padding: 10px;">
											<button id="exportPdfBtn" type="button" title="Export Content as PDF" class="btn export-pdf-btn pull-right"><i id="exportPdfBtnIcn" class="fa fa-file-pdf-o" aria-hidden="true"></i> PDF</button>
										</div>
										</div>
									</div>

									<div class="panel-body">
										<div>
											<div class="panel panel-default">
												<div id="item${appNum}-settings" class="panel-heading">
													<h1 class="panel-title subpanel-title-text">Setting Data:</h1>
												</div>
												<div class="panel-body">
													<div><pre class="pre-scroll mapDataFmt">${setDesc.toString().replaceAll("\n", "<br>")}</pre></div>
												</div>
											</div>

											<div class="panel panel-default">
												<div id="item${appNum}-state" class="panel-heading">
													<h1 class="panel-title subpanel-title-text">State Data:</h1>
												</div>
												<div class="panel-body">
													<div><pre class="pre-scroll mapDataFmt">${stateDesc.toString().replaceAll("\n", "<br>")}</pre></div>
												</div>
											</div>
										</div>
									</div>

								</div>
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
					    window.location.replace('${getAppEndpointUrl("diagHome")}');
					});
				</script>
			</body>
		"""
/* """ */
		render contentType: "text/html", data: html
	} catch (ex) { log.error "renderManagerData Exception:", ex }
}

def renderAutomationData() {
//	try {
		def appHtml = ""
		def navHtml = ""
		def scrStr = ""
		def appNum = 1
		def theChildren = parent.getTheChildren()
		//parent.getAllChildApps()?.sort {it?.getLabel()}?.each { cApp ->
		theChildren?.sort {it?.label}?.each { cApp ->
			def navMap = [:]
			if( !(cApp?.name?.contains("Diagnostic") || cApp?.label?.contains("Diagnostic")) ) {

				//navMap = ["key":cApp?.getLabel(), "items":["Settings", "State", "MetaData"]]
				navMap = ["key":cApp?.label, "items":["Settings", "State"]]
				def navItems = navHtmlBuilder(navMap, appNum)
				if(navItems?.html) { navHtml += navItems?.html }
				if(navItems?.js) { scrStr += navItems?.js }
				//def setDesc = getMapDescStr(cApp?.getSettings())
				def setDesc = getMapDescStr(cApp?.getSettingVal(null))

				def noShow = ["authToken", "accessToken", "remDiagLogDataStore" ]
				def stData = cApp.getState()?.sort()?.findAll { !(it.key in noShow) }
				def stateData = []
				["eTempTbl", "fanTbl", "hspTbl", "cspTbl", "humTbl", "oprStTbl", "tempTbl", "WdewTbl", "WhumTbl", "Wtemp" ]?.each { oi->
					stData?.each { if(it?.key?.toString().startsWith(oi)) { stateData.push(it?.key)} }
				}
				def stData1 = stData?.sort()?.findAll { !(it.key in stateData) }
				def stateDesc = getMapDescStr(stData1)

				//def metaDesc = getMapDescStr(cApp?.getMetadata())
				appHtml += """
				<div class="panel panel-primary">
					<div id="key-item${appNum}" class="panel-heading">
						<h1 class="panel-title panel-title-text">${cApp?.label}:</h1>
					</div>
					<div class="panel-body">
						<div>
							<div class="panel panel-default">
								<div id="item${appNum}-settings" class="panel-heading">
									<h1 class="panel-title subpanel-title-text">Setting Data:</h1>
								</div>
								<div class="panel-body">
									<div><pre class="mapDataFmt">${setDesc.toString().replaceAll("\n", "<br>")}</pre></div>
								</div>
							</div>

							<div class="panel panel-default">
								<div id="item${appNum}-state" class="panel-heading">
									<h1 class="panel-title subpanel-title-text">State Data:</h1>
								</div>
								<div class="panel-body">
									<div><pre class="mapDataFmt">${stateDesc.toString().replaceAll("\n", "<br>")}</pre></div>
								</div>
							</div>

						</div>
					</div>
				</div>
				"""
			}
			appNum = appNum+1
		}
		def tit = "(${parent.getStateVal("structureName")}) Automation Data"
		def html = """
			<head>
				${getWebHeaderHtml(tit)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages.min.css">
				<style>
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> ${tit}</img></h3>
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
								${appHtml}
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
					    window.location.replace('${getAppEndpointUrl("diagHome")}');
					});
				</script>
			</body>
		"""
/* """ */
		render contentType: "text/html", data: html
//	} catch (ex) { log.error "renderAutomationData Exception:", ex }
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

def renderDeviceData() {
	try {
		def devHtml = ""
		def navHtml = ""
		def scrStr = ""
		def devices = parent.getDevices()
		def devNum = 1
		devices?.sort {it?.label}.each { dev ->
			def navMap = [:]
			navMap = ["key":dev?.label, "items":["Settings", "State", "Attributes", "Commands", "Capabilities"]]
			def navItems = navHtmlBuilder(navMap, devNum)
			if(navItems?.html) { navHtml += navItems?.html }
			if(navItems?.js) { scrStr += navItems?.js }
			//def setDesc = getMapDescStr(dev?.getSettings())
			def setDesc = getMapDescStr(dev?.getSettingVal(null))
			def stateDesc = getMapDescStr(dev?.getState()?.findAll { !(it?.key in ["cssData"]) })

			def attrDesc = ""; def cnt = 1
			def devData = dev?.supportedAttributes.collect { it as String }
			devData?.sort().each {
				attrDesc += "${cnt>1 ? "\n\n" : "\n"} • ${"$it" as String}: (${dev.currentValue("$it")})"
				cnt = cnt+1
			}

			def commDesc = ""; cnt = 1
			dev?.supportedCommands?.sort()?.each { cmd ->
				commDesc += "${cnt>1 ? "\n\n" : "\n"} • ${cmd.name}(${!cmd?.arguments ? "" : cmd?.arguments.toString().toLowerCase().replaceAll("\\[|\\]", "")})"
				cnt = cnt+1
			}
			def data = dev?.capabilities?.sort()?.collect {it as String}
			def t0 = [ "capabilities":data ]
			def capDesc = getMapDescStr(t0)
			devHtml += """
			<div class="panel panel-primary">
				<div id="key-item${devNum}" class="panel-heading">
					<h1 class="panel-title panel-title-text">${dev?.label}:</h1>
				</div>
				<div class="panel-body">
					<div>
						<div id="item${devNum}-settings" class="panel panel-default">
							<div class="panel-heading">
								<h1 class="panel-title subpanel-title-text">Setting Data:</h1>
							</div>
							<div class="panel-body">
								<div><pre class="mapDataFmt">${setDesc.toString().replaceAll("\n", "<br>")}</pre></div>
							</div>
						</div>
						<div id="item${devNum}-state" class="panel panel-default">
							<div class="panel-heading">
								<h1 class="panel-title subpanel-title-text">State Data:</h1>
							</div>
							<div class="panel-body">
								<div><pre class="mapDataFmt">${stateDesc.toString().replaceAll("\n", "<br>")}</pre></div>
							</div>
						</div>
						<div id="item${devNum}-attributes" class="panel panel-default">
							<div class="panel-heading">
								<h1 class="panel-title subpanel-title-text">Attribute Data:</h1>
							</div>
							<div class="panel-body">
								<div><pre class="mapDataFmt">${attrDesc.toString().replaceAll("\n", "<br>")}</pre></div>
							</div>
						</div>
						<div id="item${devNum}-commands" class="panel panel-default">
							<div class="panel-heading">
								<h1 class="panel-title subpanel-title-text">Command Data:</h1>
							</div>
							<div class="panel-body">
								<div><pre class="mapDataFmt">${commDesc.toString().replaceAll("\n", "<br>")}</pre></div>
							</div>
						</div>
						<div id="item${devNum}-capabilities" class="panel panel-default">
							<div class="panel-heading">
								<h1 class="panel-title panel-title-text">Capability Data:</h1>
							</div>
							<div class="panel-body">
								<div><pre class="mapDataFmt">${capDesc.toString().replaceAll("\n", "<br>")}</pre></div>
							</div>
						</div>
					</div>
				</div>
			</div>
			"""
			devNum = devNum+1
		}
		def tit = "(${parent.getStateVal("structureName")}) Device Data"
		def html = """
			<head>
				${getWebHeaderHtml(tit, true, true, true, true)}
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> Device Data</img></h3>
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
					    window.location.replace('${getAppEndpointUrl("diagHome")}');
					});
				</script>
			</body>
		"""
	/* """ */
		//log.debug apiServerUrl("/api/rooms")
		render contentType: "text/html", data: html
	} catch (ex) { log.error "renderDeviceData Exception:", ex }
}

def getTstatTiles() {
	return renderDeviceTiles("Nest Thermostat")
}

def getProtectTiles() {
	return renderDeviceTiles("Nest Protect")
}

def getCamTiles() {
	return renderDeviceTiles("Nest Camera")
}

def renderDeviceTiles(type=null) {
	try {
		def devHtml = ""
		def navHtml = ""
		def scrStr = ""
		def devices = parent.getDevices()
		def devNum = 1
		devices?.sort {it?.label}.each { dev ->
			def navMap = [:]
			def hasHtml = (dev?.hasHtml() == true)
			if((hasHtml && !type) || (hasHtml && type && dev?.name == type)) {
				navMap = ["key":dev?.label, "items":[]]
				def navItems = navHtmlBuilder(navMap, devNum)
				if(navItems?.html) { navHtml += navItems?.html }
				if(navItems?.js) { scrStr += navItems?.js }
				devHtml += """
				<div class="panel panel-primary" style="max-width: 600px; margin: 30 auto; position: relative;">
					<div id="key-item${devNum}" class="panel-heading">
						<h1 class="panel-title panel-title-text">${dev?.label}: (v${dev?.devVer()})</h1>
					</div>
					<div class="panel-body">
						<div style="margin: auto; position: relative;">
							<div>${dev?.getDeviceTile(devNum)}</div>
						</div>
					</div>
				</div>
				"""
			}
			devNum = devNum+1
		}
		def tit = "(${parent.getStateVal("structureName")}) ${type}"
		def html = """
			<head>
				${getWebHeaderHtml(tit, true, true, true, true)}
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> ${type ?: "All Device"}s</img></h3>
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
					    window.location.replace('${getAppEndpointUrl("diagHome")}');
					});
				</script>
			</body>
		"""
/* """ */
		render contentType: "text/html", data: html
	} catch (ex) { log.error "renderDeviceData Exception:", ex }
}

/*
def renderAppData() {
	renderHtmlMapDesc("AppFile Data", "AppFile Data", getMapDescStr(atomicState?.appData))
}

def renderHtmlMapDesc(title, heading, datamap) {
	try {
		def navHtml = ""
		def html = """
			<head>
				${getWebHeaderHtml(title)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages.css">
				<style>
				</style>
			</head>
			<body>
				<button onclick="topFunction()" id="scrollTopBtn" title="Go to top"><i class="fa fa-arrow-up centerText" aria-hidden="true"></i> Back to Top</button>
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
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/nst_manager_5.png"> ${heading}</img></h3>
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
								<div class="panel panel-primary">
									<div class="panel-heading">
										<h1 class="panel-title panel-title-text">${heading}:</h1>
									</div>
									<div class="panel-body">
										<div><pre class="mapDataFmt">${datamap.toString().replaceAll("\n", "<br>")}</pre></div>
									</div>
								</div>
							</div>
						</div>
					</div>
				</div>
				<script src="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/js/diagpages.min.js"></script>
				<script>
					\$("#goHomeBtn").click(function() {
						closeNavMenu();
						toggleMenuBtn();
						window.location.replace('${getAppEndpointUrl("diagHome")}');
					});
				</script>
			</body>
		"""
		render contentType: "text/html", data: html
	} catch (ex) { log.error "getAppDataFile Exception:", ex }
}

*/

/********************************************************************************
|		SCHEDULE, MODE, or MOTION CHANGES ADJUST THERMOSTAT SETPOINTS			|
|		(AND THERMOSTAT MODE) AUTOMATION CODE									|
*********************************************************************************/

/*
def getTstatAutoDevId() {
	if(settings?.schMotTstat) { return settings?.schMotTstat.deviceNetworkId.toString() }
	return null
}

private tempRangeValues() {
	return (getTemperatureScale() == "C") ? "10..32" : "50..90"
}
*/

private timeComparisonOptionValues() {
	return ["custom time", "midnight", "sunrise", "noon", "sunset"]
}

private timeDayOfWeekOptions() {
	return ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
}

private getDayOfWeekName(date = null) {
	if (!date) {
		date = adjustTime()
	}
	def theDay = date.day.toInteger()
	def list = []
	list = timeDayOfWeekOptions()
	//LogAction("theDay: $theDay date.date: ${date.day}")
	return(list[theDay].toString())
/*
	switch (date.day) {
		case 0: return "Sunday"
		case 1: return "Monday"
		case 2: return "Tuesday"
		case 3: return "Wednesday"
		case 4: return "Thursday"
		case 5: return "Friday"
		case 6: return "Saturday"
	}
*/
	return null
}

/*
private getDayOfWeekNumber(date = null) {
	if (!date) {
		date = adjustTime(now())
	}
	if (date instanceof Date) {
		return date.day
	}
	switch (date) {
		case "Sunday": return 0
		case "Monday": return 1
		case "Tuesday": return 2
		case "Wednesday": return 3
		case "Thursday": return 4
		case "Friday": return 5
		case "Saturday": return 6
	}
	return null
}
*/

//adjusts the time to local timezone
private adjustTime(time = null) {
	if (time instanceof String) {
		//get UTC time
		time = timeToday(time, location.timeZone).getTime()
	}
	if (time instanceof Date) {
		//get unix time
		time = time.getTime()
	}
	if (!time) {
		time = now()
	}
	if (time) {
		return new Date(time + location.timeZone.getOffset(time))
	}
	return null
}

private formatLocalTime(time, format = "EEE, MMM d yyyy @ h:mm a z") {
	if (time instanceof Long) {
		time = new Date(time)
	}
	if (time instanceof String) {
		//get UTC time
		time = timeToday(time, location.timeZone)
	}
	if (!(time instanceof Date)) {
		return null
	}
	def formatter = new java.text.SimpleDateFormat(format)
	formatter.setTimeZone(location.timeZone)
	return formatter.format(time)
}

private convertDateToUnixTime(date) {
	if (!date) {
		return null
	}
	if (!(date instanceof Date)) {
		date = new Date(date)
	}
	return date.time - location.timeZone.getOffset(date.time)
}

private convertTimeToUnixTime(time) {
	if (!time) {
		return null
	}
	return time - location.timeZone.getOffset(time)
}

private formatTime(time, zone = null) {
	//we accept both a Date or a settings' Time
	return formatLocalTime(time, "h:mm a${zone ? " z" : ""}")
}

private formatHour(h) {
	return (h == 0 ? "midnight" : (h < 12 ? "${h} AM" : (h == 12 ? "noon" : "${h-12} PM"))).toString()
}
/*

private cleanUpMap(map) {
	def washer = []
	//find dirty laundry
	for (item in map) {
		if (item.value == null) washer.push(item.key)
	}
	//clean it
	for (item in washer) {
		map.remove(item)
	}
	washer = null
	return map
}

private buildDeviceNameList(devices, suffix) {
	def cnt = 1
	def result = ""
	for (device in devices) {
		def label = getDeviceLabel(device)
		result += "$label" + (cnt < devices.size() ? (cnt == devices.size() - 1 ? " $suffix " : ", ") : "")
		cnt++
	}
	if(result == "") { result = null }
	return result
}

private getDeviceLabel(device) {
	return device instanceof String ? device : (device ? ( device.label ? device.label : (device.name ? device.name : "$device")) : "Unknown device")
}

def getCurrentSchedule() {
	def noSched = false
	def mySched

	def schedList = getScheduleList()
	def res1
	def ccnt = 1
	for (cnt in schedList) {
		res1 = checkRestriction(cnt)
		if(res1 == null) { break }
		ccnt += 1
	}
	if(ccnt > schedList?.size()) { noSched = true }
	else { mySched = ccnt }
	if(mySched != null) {
		LogTrace("getCurrentSchedule: mySched: $mySched noSched: $noSched ccnt: $ccnt res1: $res1")
	}
	return mySched
}

private checkRestriction(cnt) {
	//LogTrace("checkRestriction:( $cnt )")
	def sLbl = "schMot_${cnt}_"
	def restriction
	def act = settings["${sLbl}SchedActive"]
	if(act) {
		def apprestrict = state?."sched${cnt}restrictions"

		if (apprestrict?.m && apprestrict?.m.size() && !(location.mode in apprestrict?.m)) {
			restriction = "a HE MODE mismatch"
		} else if (apprestrict?.w && apprestrict?.w.size() && !(getDayOfWeekName() in apprestrict?.w)) {
			restriction = "a day of week mismatch"
		} else if (apprestrict?.tf && apprestrict?.tt && !(checkTimeCondition(apprestrict?.tf, apprestrict?.tfc, apprestrict?.tfo, apprestrict?.tt, apprestrict?.ttc, apprestrict?.tto))) {
			restriction = "a time of day mismatch"
		} else {
			if (settings["${sLbl}rstrctSWOn"]) {
				for(sw in settings["${sLbl}rstrctSWOn"]) {
					if (sw.currentValue("switch") != "on") {
						restriction = "switch ${sw} being ${sw.currentValue("switch")}"
						break
					}
				}
			}
			if (!restriction && settings["${sLbl}rstrctSWOff"]) {
				for(sw in settings["${sLbl}rstrctSWOff"]) {
					if (sw.currentValue("switch") != "off") {
						restriction = "switch ${sw} being ${sw.currentValue("switch")}"
						break
					}
				}
			}
			if (!restriction && settings["${sLbl}rstrctPHome"] && !isSomebodyHome(settings["${sLbl}rstrctPHome"])) {
				for(pr in settings["${sLbl}rstrctPHome"]) {
					if (!isPresenceHome(pr)) {
						restriction = "presence ${pr} being ${pr.currentValue("presence")}"
						break
					}
				}
			}
			if (!restriction && settings["${sLbl}rstrctPAway"] && isSomebodyHome(settings["${sLbl}rstrctPAway"])) {
				for(pr in settings["${sLbl}rstrctPAway"]) {
					if (isPresenceHome(pr)) {
						restriction = "presence ${pr} being ${pr.currentValue("presence")}"
						break
					}
				}
			}
		}
		LogTrace("checkRestriction:( $cnt ) restriction: $restriction")
	} else {
		restriction = "an inactive schedule"
	}
	return restriction
}

def getActiveScheduleState() {
	return state?.activeSchedData ?: null
}

def getSchRestrictDoWOk(cnt) {
	def apprestrict = state?.activeSchedData
	def result = true
	apprestrict?.each { sch ->
		if(sch?.key.toInteger() == cnt.toInteger()) {
			if (!(getDayOfWeekName().toString() in sch?.value?.w)) {
				result = false
			}
		}
	}
	return result
}
*/
private checkTimeCondition(timeFrom, timeFromCustom, timeFromOffset, timeTo, timeToCustom, timeToOffset) {
	def time = adjustTime()
	//convert to minutes since midnight
	def tc = time.hours * 60 + time.minutes
	def tf
	def tt
	def i = 0
	while (i < 2) {
		def t = null
		def h = null
		def m = null
		switch(i == 0 ? timeFrom : timeTo) {
			case "custom time":
				t = adjustTime(i == 0 ? timeFromCustom : timeToCustom)
				if (i == 0) {
					timeFromOffset = 0
				} else {
					timeToOffset = 0
				}
				break
			case "sunrise":
				t = getSunrise()
				break
			case "sunset":
				t = getSunset()
				break
			case "noon":
				h = 12
				break
			case "midnight":
				h = (i == 0 ? 0 : 24)
			break
		}
		if (h != null) {
			m = 0
		} else {
			h = t.hours
			m = t.minutes
		}
		switch (i) {
			case 0:
				tf = h * 60 + m + cast(timeFromOffset, "number")
				break
			case 1:
				tt = h * 60 + m + cast(timeFromOffset, "number")
				break
		}
		i += 1
	}
	//due to offsets, let's make sure all times are within 0-1440 minutes
	while (tf < 0) tf += 1440
	while (tf > 1440) tf -= 1440
	while (tt < 0) tt += 1440
	while (tt > 1440) tt -= 1440
	if (tf < tt) {
		return (tc >= tf) && (tc < tt)
	} else {
		return (tc < tt) || (tc >= tf)
	}
}

private cast(value, dataType) {
	def trueStrings = ["1", "on", "open", "locked", "active", "wet", "detected", "present", "occupied", "muted", "sleeping"]
	def falseStrings = ["0", "false", "off", "closed", "unlocked", "inactive", "dry", "clear", "not detected", "not present", "not occupied", "unmuted", "not sleeping"]
	switch (dataType) {
		case "string":
		case "text":
			if (value instanceof Boolean) {
				return value ? "true" : "false"
			}
			return value ? "$value" : ""
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
		case "long":
			if (value == null) return (long) 0
			if (value instanceof String) {
				if (value.isInteger())
					return (long) value.toInteger()
				if (value.isFloat())
					return (long) Math.round(value.toFloat())
				if (value in trueStrings)
					return (long) 1
			}
			def result = (long) 0
			try {
				result = (long) value
			} catch(all) {
			}
			return result ? result : (long) 0
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
		case "boolean":
			if (value instanceof String) {
				if (!value || (value in falseStrings))
					return false
				return true
			}
			return !!value
		case "time":
			return value instanceof String ? adjustTime(value).time : cast(value, "long")
		case "vector3":
			return value instanceof String ? adjustTime(value).time : cast(value, "long")
	}
	return value
}

//TODO is this expensive?
private getSunrise() {
	def sunTimes = getSunriseAndSunset()
	return adjustTime(sunTimes.sunrise)
}

private getSunset() {
	def sunTimes = getSunriseAndSunset()
	return adjustTime(sunTimes.sunset)
}


/*
def okSym() {
	return "✓"// ☑"
}
def notOkSym() {
	return "✘"
}
*/

/*

def getRemSenTempSrc() {
	return state?.remoteTempSourceStr ?: null
}

def getAbrevDay(vals) {
	def list = []
	if(vals) {
		//log.debug "days: $vals | (${vals?.size()})"
		def len = (vals?.toString().length() < 7) ? 3 : 2
		vals?.each { d ->
			list.push(d?.toString().substring(0, len))
		}
	}
	return list
}

def roundTemp(Double temp) {
	if(temp == null) { return null }
	def newtemp
	if( getTemperatureScale() == "C") {
		newtemp = Math.round(temp.round(1) * 2) / 2.0f
	} else {
		if(temp instanceof Integer) {
			//log.debug "roundTemp: ($temp) is Integer"
			newTemp = temp.toInteger()
		}
		else if(temp instanceof Double) {
			//log.debug "roundTemp: ($temp) is Double"
			newtemp = temp.round(0).toInteger()
		}
		else if(temp instanceof BigDecimal) {
			//log.debug "roundTemp: ($temp) is BigDecimal"
			newtemp = temp.toInteger()
		}
	}
	return newtemp
}

def updateScheduleStateMap() {
	if(autoType == "schMot" && isSchMotConfigured()) {
		def actSchedules = null
		def numAct = 0
		actSchedules = [:]
		getScheduleList()?.each { scdNum ->
			def sLbl = "schMot_${scdNum}_"
			def newScd = [:]
			def schActive = settings["${sLbl}SchedActive"]

			if(schActive) {
				actSchedules?."${scdNum}" = [:]
				newScd = cleanUpMap([
					lbl: settings["${sLbl}name"],
					m: settings["${sLbl}rstrctMode"],
					tf: settings["${sLbl}rstrctTimeFrom"],
					tfc: settings["${sLbl}rstrctTimeFromCustom"],
					tfo: settings["${sLbl}rstrctTimeFromOffset"],
					tt: settings["${sLbl}rstrctTimeTo"],
					ttc: settings["${sLbl}rstrctTimeToCustom"],
					tto: settings["${sLbl}rstrctTimeToOffset"],
					w: settings["${sLbl}restrictionDOW"],
					p1: deviceInputToList(settings["${sLbl}rstrctPHome"]),
					p0: deviceInputToList(settings["${sLbl}rstrctPAway"]),
					s1: deviceInputToList(settings["${sLbl}rstrctSWOn"]),
					s0: deviceInputToList(settings["${sLbl}rstrctSWOff"]),
					ctemp: roundTemp(settings["${sLbl}CoolTemp"]),
					htemp: roundTemp(settings["${sLbl}HeatTemp"]),
					hvacm: settings["${sLbl}HvacMode"],
					sen0: settings["schMotRemoteSensor"] ? deviceInputToList(settings["${sLbl}remSensor"]) : null,
					thres: settings["schMotRemoteSensor"] ? settings["${sLbl}remSenThreshold"] : null,
					m0: deviceInputToList(settings["${sLbl}Motion"]),
					mctemp: settings["${sLbl}Motion"] ? roundTemp(settings["${sLbl}MCoolTemp"]) : null,
					mhtemp: settings["${sLbl}Motion"] ? roundTemp(settings["${sLbl}MHeatTemp"]) : null,
					mhvacm: settings["${sLbl}Motion"] ? settings["${sLbl}MHvacMode"] : null,
//					mpresHome: settings["${sLbl}Motion"] ? settings["${sLbl}MPresHome"] : null,
//					mpresAway: settings["${sLbl}Motion"] ? settings["${sLbl}MPresAway"] : null,
					mdelayOn: settings["${sLbl}Motion"] ? settings["${sLbl}MDelayValOn"] : null,
					mdelayOff: settings["${sLbl}Motion"] ? settings["${sLbl}MDelayValOff"] : null
				])
				numAct += 1
				actSchedules?."${scdNum}" = newScd
				//LogAction("updateScheduleMap [ ScheduleNum: $scdNum | PrefixLbl: $sLbl | SchedActive: $schActive | NewSchedData: $newScd ]", "info", false)
			}
		}
		state.activeSchedData = actSchedules
	}
}

def deviceInputToList(items) {
	def list = []
	if(items) {
		items?.sort().each { d ->
			list.push(d?.displayName.toString())
		}
		return list
	}
	return null
}
*/

/*
def inputItemsToList(items) {
	def list = []
	if(items) {
		items?.each { d ->
			list.push(d)
		}
		return list
	}
	return null
}
*/

/*
def isSchMotConfigured() {
	return (settings?.schMotTstat && (
					settings?.schMotOperateFan ||
					settings?.schMotRemoteSensor ||
					settings?.schMotWaterOff ||
					settings?.schMotContactOff ||
					settings?.schMotHumidityControl ||
					settings?.schMotExternalTempOff)) ? true : false
}

def getAutoRunSec() { return !state?.autoRunDt ? 100000 : GetTimeDiffSeconds(state?.autoRunDt, null, "getAutoRunSec").toInteger() }

def schMotCheck() {
	LogTrace("schMotCheck")
	try {
		if(getIsAutomationDisabled()) { return }
		def schWaitVal = settings?.schMotWaitVal?.toInteger() ?: 60
		if(schWaitVal > 120) { schWaitVal = 120 }
		def t0 = getAutoRunSec()
		if(t0 < schWaitVal) {
			def schChkVal = ((schWaitVal - t0) < 30) ? 30 : (schWaitVal - t0)
			scheduleAutomationEval(schChkVal)
			LogAction("Too Soon to Evaluate Actions; Re-Evaluation in (${schChkVal} seconds)", "info", false)
			return
		}

		def execTime = now()
		state?.autoRunDt = getDtNow()

		// This order is important
		// turn system on/off, then update schedule mode/temps, then remote sensors, then update fans

		def updatedWeather = false
		if(settings?.schMotWaterOff) {
			if(isLeakWatConfigured()) { leakWatCheck() }
		}
		if(settings?.schMotContactOff) {
			if(isConWatConfigured()) { conWatCheck() }
		}
		if(settings?.schMotExternalTempOff) {
			if(isExtTmpConfigured()) {
				if(settings?.extTmpUseWeather && !updatedWeather) { updatedWeather = true; getExtConditions() }
				extTmpTempCheck()
			}
		}
//		if(settings?.schMotSetTstatTemp) {
			if(isTstatSchedConfigured()) { setTstatTempCheck() }
//		}
		if(settings?.schMotRemoteSensor) {
			if(isRemSenConfigured()) {
				remSenCheck()
			}
		}
		if(settings?.schMotHumidityControl) {
			if(isHumCtrlConfigured()) {
				if(settings?.humCtrlUseWeather && !updateWeather) { getExtConditions() }
				humCtrlCheck()
			}
		}
		if(settings?.schMotOperateFan) {
			if(isFanCtrlConfigured()) {
				fanCtrlCheck()
			}
		}

		storeExecutionHistory((now() - execTime), "schMotCheck")
	} catch (ex) {
		log.error "schMotCheck Exception:", ex
		//parent?.sendExceptionData(ex, "schMotCheck", true, getAutoType())
	}
}
*/

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
	try {
		if(method) {
			LogTrace("${method} Execution Time: (${val} milliseconds)")
		}
		if(method in ["watchDogCheck", "checkNestMode", "schMotCheck"]) {
			state?.autoExecMS = val ?: null
			def list = state?.evalExecutionHistory ?: []
			def listSize = 20
			list = addToList(val, list, listSize)
			if(list) { state?.evalExecutionHistory = list }
		}
		//if(!(method in ["watchDogCheck", "checkNestMode"])) {
			def list = state?.detailExecutionHistory ?: []
			def listSize = 30
			list = addToList([val, method, getDtNow()], list, listSize)
			if(list) { state?.detailExecutionHistory = list }
		//}
	} catch (ex) {
		log.error "storeExecutionHistory Exception:", ex
		//parent?.sendExceptionData(ex, "storeExecutionHistory", true, getAutoType())
	}
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

/*
def getAverageValue(items) {
	def tmpAvg = []
	def val = 0
	if(!items) { return val }
	else if(items?.size() > 1) {
		tmpAvg = items
		if(tmpAvg && tmpAvg?.size() > 1) { val = (tmpAvg?.sum().toDouble() / tmpAvg?.size().toDouble()).round(0) }
	} else { val = item }
	return val.toInteger()
}
*/
/************************************************************************************************
|								DYNAMIC NOTIFICATION PAGES								|
*************************************************************************************************/

/*
def setNotificationPage1(params) {
	//href "setNotificationPage1", title: titles("t_nt"), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null), image: getAppImg("i_not")
	LogTrace("setNotificationPage1()")
	def pName = watchDogPrefix()
	def t0 = ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage2(params) {
	//href "setNotificationPage2", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":false, "allowAlarm":false, "showSchedule":true], state: (pageDesc ? "complete" : null)
	LogTrace("setNotificationPage2()")
	def pName = nModePrefix()
	def t0 = ["pName":"${pName}", "allowSpeech":false, "allowAlarm":false, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage3(params) {
	//href "setNotificationPage3", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	pName = leakWatPrefix()
	LogTrace("setNotificationPage3()")
	def t0 = ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage4(params) {
	//href "setNotificationPage4, title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	pName = conWatPrefix()
	LogTrace("setNotificationPage4()")
	def t0 = ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage5(params) {
	//href "setNotificationPage5", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	pName = extTmpPrefix()
	LogTrace("setNotificationPage5()")
	def t0 = ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage(params) {
	def pName = params?.pName
	def allowSpeech = false
	def allowAlarm = false
	def showSched = false
	if(params?.pName) {
		state.t_notifD = params
		allowSpeech = params?.allowSpeech?.toBoolean(); showSched = params?.showSchedule?.toBoolean(); allowAlarm = params?.allowAlarm?.toBoolean()
	} else {
		pName = state?.t_notifD?.pName; allowSpeech = state?.t_notifD?.allowSpeech; showSched = state?.t_notifD?.showSchedule; allowAlarm = state?.t_notifD?.allowAlarm
	}
	if(pName == null) { return }
	dynamicPage(name: "setNotificationPage", title: "Configure Notification Options", uninstall: false) {
		section("") {
		//section("Notification Preferences:") {
			input "${pName}NotifOn", "bool", title: imgTitle(getAppImg("i_not"), inputTitleStr("Enable Notifications?")), description: (!settings["${pName}NotifOn"] ? "Enable Text, Voice, or Alarm Notifications" : ""), required: false, defaultValue: false, submitOnChange: true
			def fixSettings = false
			if(settings["${pName}NotifOn"]) {
//				section("Use NST Manager Settings:") {
					input "${pName}UseMgrNotif", "bool", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Use Manager Settings?")), defaultValue: true, submitOnChange: true, required: false
//				}
				if(settings?."${pName}UseMgrNotif" == false) {
		//			section("Enable Text Messaging:") {
						input "${pName}NotifPhones", "phone", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Send SMS to Number (Optional)")), required: false, submitOnChange: true
		//			}
		//			section("Enable Pushover Support:") {
						input "${pName}PushoverEnabled", "bool", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
						if(settings?."${pName}PushoverEnabled" == true) {
							input "${pName}PushoverDevices", "capability.notification", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
						}
		//			}
				} else {
					fixSettings = true
				}
			} else {
				fixSettings = true
			}
			if(fixSettings) {
				settingRemove("${pName}NotifPhones")
				settingRemove("${pName}PushoverEnabled")
				settingRemove("${pName}PushoverDevices")
				//settingRemove("${pName}UseParentNotifRestrictions")
			}
*/
/*
			if(allowSpeech && settings?."${pName}NotifOn") {
//			section("Voice Notification Preferences:") {
				input "${pName}AllowSpeechNotif", "bool", title: "Enable Voice Notifications?", description: "Media players, or Speech Devices", required: false, defaultValue: (settings?."${pName}AllowSpeechNotif" ? true : false), submitOnChange: true, image: getAppImg("speech_icon.png")
				if(settings["${pName}AllowSpeechNotif"]) {
					setInitialVoiceMsgs(pName)
					input "${pName}SendToAskAlexaQueue", "bool", title: "Send to Ask Alexa Message Queue?", required: false, defaultValue: (settings?."${pName}AllowSpeechNotif" ? false : true), submitOnChange: true,
							image: askAlexaImgUrl()
					input "${pName}SpeechMediaPlayer", "capability.musicPlayer", title: "Select Media Player(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg("media_player.png")
					input "${pName}EchoDevices", "device.echoSpeaksDevice", title: "Select Alexa Devices(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg('echo_speaks.png')
					input "${pName}SpeechDevices", "capability.speechSynthesis", title: "Select Speech Synthesizer(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg("speech2_icon.png")
					if(settings["${pName}SpeechMediaPlayer"] || settings["${pName}EchoDevices"]) {
						input "${pName}SpeechVolumeLevel", "number", title: "Default Volume Level?", required: false, defaultValue: 30, range: "0::100", submitOnChange: true, image: getAppImg("volume_icon.png")
						if(settings["${pName}SpeechMediaPlayer"]) {
							input "${pName}SpeechAllowResume", "bool", title: "Can Resume Playing Media?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("resume_icon.png")
						}
					}
					def desc = ""
					if(pName in ["conWat", "extTmp", "leakWat"]) {
						if( (settings["${pName}SpeechMediaPlayer"] || settings["${pName}SpeechDevices"] || settings["${pName}EchoDevices"] || settings["${pName}SendToAskAlexaQueue"]) ) {
							switch(pName) {
								case "conWat":
									desc = "Contact Close"
									break
								case "extTmp":
									desc = "External Temperature Threshold"
									break
								case "leakWat":
									desc = "Water Dried"
									break
							}

							input "${pName}SpeechOnRestore", "bool", title: "Speak when restoring HVAC on (${desc})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("speech_icon.png")
							// TODO: There are more messages and errors than ON / OFF
							input "${pName}UseCustomSpeechNotifMsg", "bool", title: "Customize Notitification Message?", required: false, defaultValue: (settings?."${pName}AllowSpeechNotif" ? false : true), submitOnChange: true,
								image: getAppImg("speech_icon.png")
							if(settings["${pName}UseCustomSpeechNotifMsg"]) {
								getNotifVariables(pName)
								input "${pName}CustomOffSpeechMessage", "text", title: "Turn Off Message?", required: false, defaultValue: state?."${pName}OffVoiceMsg" , submitOnChange: true, image: getAppImg("speech_icon.png")
								state?."${pName}OffVoiceMsg" = settings?."${pName}CustomOffSpeechMessage"
								if(settings?."${pName}CustomOffSpeechMessage") {
									paragraph "Off Msg:\n" + voiceNotifString(state?."${pName}OffVoiceMsg",pName)
								}
								input "${pName}CustomOnSpeechMessage", "text", title: "Restore On Message?", required: false, defaultValue: state?."${pName}OnVoiceMsg", submitOnChange: true, image: getAppImg("speech_icon.png")
								state?."${pName}OnVoiceMsg" = settings?."${pName}CustomOnSpeechMessage"
								if(settings?."${pName}CustomOnSpeechMessage") {
									paragraph "Restore On Msg:\n" + voiceNotifString(state?."${pName}OnVoiceMsg",pName)
								}
							} else {
								state?."${pName}OffVoiceMsg" = ""
								state?."${pName}OnVoiceMsg" = ""
							}
						}
					}
				}
			//}
		}
*/
/*
			if(allowAlarm && settings?."${pName}NotifOn") {
	//			section("Alarm/Siren Device Preferences:") {
					input "${pName}AllowAlarmNotif", "bool", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Enable Alarm | Siren?")), required: false, defaultValue: (settings?."${pName}AllowAlarmNotif" ? true : false), submitOnChange: true
					if(settings["${pName}AllowAlarmNotif"]) {
						input "${pName}AlarmDevices", "capability.alarm", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Select Alarm/Siren(s)")), multiple: true, required: settings["${pName}AllowAlarmNotif"], submitOnChange: true
					}
	//			}
			}
*/
/*

			if(pName in ["conWat", "leakWat", "extTmp", "watchDog"] && settings["${pName}NotifOn"] && settings["${pName}AllowAlarmNotif"] && settings["${pName}AlarmDevices"]) {
	//			section("Notification Alert Options (1):") {
					input "${pName}_Alert_1_Delay", "enum", title: imgTitle(getAppImg("alert_icon2.png"), inputTitleStr("First Alert Delay (in minutes)")), defaultValue: null, required: true, submitOnChange: true, options: longTimeSecEnum()
					if(settings?."${pName}_Alert_1_Delay") {
						input "${pName}_Alert_1_AlarmType", "enum", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Alarm Type to use?")), options: alarmActionsEnum(), defaultValue: null, submitOnChange: true, required: true
						if(settings?."${pName}_Alert_1_AlarmType") {
							input "${pName}_Alert_1_Alarm_Runtime", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Turn off Alarm After (in seconds)?")), options: shortTimeEnum(), defaultValue: 10, required: true, submitOnChange: true
						}
					}
	//			}
				if(settings["${pName}_Alert_1_Delay"]) {
	//				section("Notification Alert Options (2):") {
						input "${pName}_Alert_2_Delay", "enum", title: imgTitle(getAppImg("alert_icon2.png"), inputTitleStr("Second Alert Delay (in minutes)")), defaultValue: null, options: longTimeSecEnum(), required: false, submitOnChange: true
						if(settings?."${pName}_Alert_2_Delay") {
							input "${pName}_Alert_2_AlarmType", "enum", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Alarm Type to use?")), options: alarmActionsEnum(), defaultValue: null, submitOnChange: true, required: true
							if(settings?."${pName}_Alert_2_AlarmType") {
								input "${pName}_Alert_2_Alarm_Runtime", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Turn off Alarm After (in minutes)?")), options: shortTimeEnum(), defaultValue: 10, required: true, submitOnChange: true
							}
						}
	//				}
				}
			}
		}
	}
}
*/

//def setInitialVoiceMsgs(pName) {
/*
	if(settings["${pName}AllowSpeechNotif"]) {
		if(pName in ["conWat", "extTmp", "leakWat"]) {
			if(pName == "leakWat") {
				if(!state?."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OffVoiceMsg" = "ATTENTION: %devicename% has been turned OFF because %wetsensor% has reported it is WET" }
				if(!state?."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OnVoiceMsg" = "Restoring %devicename% to %lastmode% Mode because ALL water sensors have been Dry again for (%ondelay%)" }
			}
			if(pName == "conWat") {
				if(!state?."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OffVoiceMsg" = "ATTENTION: %devicename% has been turned OFF because %opencontact% has been Opened for (%offdelay%)" }
				if(!state?."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OnVoiceMsg" = "Restoring %devicename% to %lastmode% Mode because ALL contacts have been Closed again for (%ondelay%)" }
			}
			if(pName == "extTmp") {
				if(!state?."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OffVoiceMsg" = "ATTENTION: %devicename% has been turned to ECO because External Temp is above the temp threshold for (%offdelay%)" }
				if(!state?."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]) {
					state?."${pName}OnVoiceMsg" = "Restoring %devicename% to %lastmode% Mode because External Temp has been above the temp threshold for (%ondelay%)" }
			}
		}
	}
*/
//}

//ERS
//def setCustomVoice(pName) {
/*
	if(settings["${pName}AllowSpeechNotif"]) {
		if(pName in ["conWat", "extTmp", "leakWat"]) {
			if(settings["${pName}UseCustomSpeechNotifMsg"]) {
				state?."${pName}OffVoiceMsg" = settings?."${pName}CustomOffSpeechMessage"
				state?."${pName}OnVoiceMsg" = settings?."${pName}CustomOnSpeechMessage"
			}
		}
	}
*/
//}

/*
def setNotificationTimePage(params) {
	def pName = params?.pName
	if(params?.pName) {
		state.curNotifTimePageData = params
	} else { pName = state?.curNotifTimePageData?.pName }
	dynamicPage(name: "setNotificationTimePage", title: "Prevent Notifications\nDuring these Days, Times or Modes", uninstall: false) {
		def timeReq = (settings["${pName}qStartTime"] || settings["${pName}qStopTime"]) ? true : false
		section() {
			input "${pName}qStartInput", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("start_time_icon.png")
			if(settings["${pName}qStartInput"] == "A specific time") {
				input "${pName}qStartTime", "time", title: "Start time", required: timeReq, image: getAppImg("start_time_icon.png")
			}
			input "${pName}qStopInput", "enum", title: "Stopping at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("stop_time_icon.png")
			if(settings?."${pName}qStopInput" == "A specific time") {
				input "${pName}qStopTime", "time", title: "Stop time", required: timeReq, image: getAppImg("stop_time_icon.png")
			}
			input "${pName}quietDays", "enum", title: "Prevent during these days of the week", multiple: true, required: false, image: getAppImg("day_calendar_icon.png"), options: timeDayOfWeekOptions()
			input "${pName}quietModes", "mode", title: "Prevent when these Modes are Active", multiple: true, submitOnChange: true, required: false, image: getAppImg("i_mod")
		}
	}
}

String getNotifSchedDesc(pName) {
	def sun = getSunriseAndSunset()
	def startInput = settings?."${pName}qStartInput"
	def startTime = settings?."${pName}qStartTime"
	def stopInput = settings?."${pName}qStopInput"
	def stopTime = settings?."${pName}qStopTime"
	def dayInput = settings?."${pName}quietDays"
	def modeInput = settings?."${pName}quietModes"
	def notifDesc = ""
	if(settings?."${pName}UseParentNotifRestrictions" == false) {
		def getNotifTimeStartLbl = ( (startInput == "Sunrise" || startInput == "Sunset") ? ( (startInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (startTime ? time2Str(startTime) : "") )
		def getNotifTimeStopLbl = ( (stopInput == "Sunrise" || stopInput == "Sunset") ? ( (stopInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (stopTime ? time2Str(stopTime) : "") )
		notifDesc += (getNotifTimeStartLbl && getNotifTimeStopLbl) ? "• Silent Time: ${getNotifTimeStartLbl} - ${getNotifTimeStopLbl}" : ""
		def days = getInputToStringDesc(dayInput)
		def modes = getInputToStringDesc(modeInput)
		notifDesc += days ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl) ? "\n" : ""}• Silent Day${isPluralString(dayInput)}: ${days}" : ""
		notifDesc += modes ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl || days) ? "\n" : ""}• Silent Mode${isPluralString(modeInput)}: ${modes}" : ""
	} else {
		notifDesc += "• Using Manager Restrictions"
	}
	return (notifDesc != "") ? "${notifDesc}" : null
}

def getOk2Notify(pName) {
	return ((settings["${pName}NotifOn"] == true) && (daysOk(settings?."${pName}quietDays") == true) && (notificationTimeOk(pName) == true) && (modesOk(settings?."${pName}quietModes") == true))
}

def notificationTimeOk(pName) {
	def strtTime = null
	def stopTime = null
	def now = new Date()
	def sun = getSunriseAndSunset() // current based on geofence, previously was: def sun = getSunriseAndSunset(zipCode: zipCode)
	if(settings?."${pName}qStartTime" && settings?."${pName}qStopTime") {
		if(settings?."${pName}qStartInput" == "sunset") { strtTime = sun.sunset }
		else if(settings?."${pName}qStartInput" == "sunrise") { strtTime = sun.sunrise }
		else if(settings?."${pName}qStartInput" == "A specific time" && settings?."${pName}qStartTime") { strtTime = settings?."${pName}qStartTime" }

		if(settings?."${pName}qStopInput" == "sunset") { stopTime = sun.sunset }
		else if(settings?."${pName}qStopInput" == "sunrise") { stopTime = sun.sunrise }
		else if(settings?."${pName}qStopInput" == "A specific time" && settings?."${pName}qStopTime") { stopTime = settings?."${pName}qStopTime" }
	} else { return true }
	if(strtTime && stopTime) {
		return timeOfDayIsBetween(strtTime, stopTime, new Date(), getTimeZone()) ? false : true
	} else { return true }
}

def getNotifVariables(pName) {
	def str = ""
	str += "\n • DeviceName: %devicename%"
	str += "\n • Last Mode: %lastmode%"
	str += (pName == "leakWat") ? "\n • Wet Water Sensor: %wetsensor%" : ""
	str += (pName == "conWat") ? "\n • Open Contact: %opencontact%" : ""
	str += (pName in ["conWat", "extTmp"]) ? "\n • Off Delay: %offdelay%" : ""
	str += "\n • On Delay: %ondelay%"
	str += (pName == "extTmp") ? "\n • Temp Threshold: %tempthreshold%" : ""
	paragraph "These Variables are accepted: ${str}"
}

//process custom tokens to generate final voice message (Copied from BigTalker)
def voiceNotifString(phrase, pName) {
	//LogTrace("conWatVoiceNotifString")
	try {
		if(phrase?.toLowerCase().contains("%devicename%")) { phrase = phrase?.toLowerCase().replace('%devicename%', (settings?."schMotTstat"?.displayName.toString() ?: "unknown")) }
		if(phrase?.toLowerCase().contains("%lastmode%")) { phrase = phrase?.toLowerCase().replace('%lastmode%', (state?."${pName}RestoreMode".toString() ?: "unknown")) }
		if(pName == "leakWat" && phrase?.toLowerCase().contains("%wetsensor%")) {
			phrase = phrase?.toLowerCase().replace('%wetsensor%', (getWetWaterSensors(leakWatSensors) ? getWetWaterSensors(leakWatSensors)?.join(", ").toString() : "a selected leak sensor")) }
		if(pName == "conWat" && phrase?.toLowerCase().contains("%opencontact%")) {
			phrase = phrase?.toLowerCase().replace('%opencontact%', (getOpenContacts(conWatContacts) ? getOpenContacts(conWatContacts)?.join(", ").toString() : "a selected contact")) }
		if(pName == "extTmp" && phrase?.toLowerCase().contains("%tempthreshold%")) {
			phrase = phrase?.toLowerCase().replace('%tempthreshold%', "${extTmpDiffVal.toString()}(${tUnitStr()})") }
		if(phrase?.toLowerCase().contains("%offdelay%")) { phrase = phrase?.toLowerCase().replace('%offdelay%', getEnumValue(longTimeSecEnum(), settings?."${pName}OffDelay").toString()) }
		if(phrase?.toLowerCase().contains("%ondelay%")) { phrase = phrase?.toLowerCase().replace('%ondelay%', getEnumValue(longTimeSecEnum(), settings?."${pName}OnDelay").toString()) }
	} catch (ex) {
		log.error "voiceNotifString Exception:", ex
		//parent?.sendExceptionData(ex, "voiceNotifString", true, getAutoType())
	}
	return phrase
}
*/

def getNotifConfigDesc(pName) {
	//LogTrace("getNotifConfigDesc pName: $pName")
	def str = ""
	if(settings?."${pName}NotifOn") {
		// str += "Notification Status:"
		// if(!getRecipientDesc(pName)) {
		// 	str += "\n • Contacts: Using Manager Settings"
		// }
		def t0
		if(settings?."${pName}UseMgrNotif" == false) {
			str += (settings?."${pName}NotifPhones") ? "${str != "" ? "\n" : ""} • SMS: (${settings?."${pName}NotifPhones"?.size()})" : ""
			str += (settings?."${pName}PushoverEnabled") ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
			str += (settings?."${pName}PushoverEnabled" && settings?."${pName}PushoverDevices") ? "${str != "" ? "\n" : ""} • Pushover Devices: (${settings?."${pName}PushoverDevices"})" : ""
			//t0 = getNotifSchedDesc(pName)
			//str += t0 ? "\n\nAlert Restrictions:\n${t0}" : ""
		} else {
			str += " • Enabled Using Manager Settings"
		}
		t0 = str
		if(t0) {
			str = "Notification Settings\n${t0}"
		}
		//t0 = getVoiceNotifConfigDesc(pName)
		//str += t0 ? "\n\nVoice Status:${t0}" : ""
		t0 = getAlarmNotifConfigDesc(pName)
		str += t0 ?  "\n\nAlarm Status:${t0}" : ""
		t0 = getAlertNotifConfigDesc(pName)
		str += t0 ? "\n\n${t0}" : ""
	}
	return (str != "") ? "${str}" : null
}

/*
def getVoiceNotifConfigDesc(pName) {
	def str = ""
	if(settings?."${pName}NotifOn" && settings["${pName}AllowSpeechNotif"]) {
		def speaks = settings?."${pName}SpeechDevices"
		def medias = settings?."${pName}SpeechMediaPlayer"
		def echos = settings["${pName}EchoDevices"]
		str += settings["${pName}SendToAskAlexaQueue"] ? "\n• Send to Ask Alexa: (True)" : ""
		str += speaks ? "\n • Speech Devices:" : ""
		if(speaks) {
			def cnt = 1
			speaks?.each { str += it ? "\n ${cnt < speaks.size() ? "├" : "└"} $it" : ""; cnt = cnt+1; }
		}
		str += echos ? "\n • Alexa Devices:" : ""
		if(echos) {
			def cnt = 1
			echos?.each { str += it ? "\n ${cnt < echos.size() ? "├" : "└"} $it" : ""; cnt = cnt+1; }
			str += (echos && settings?."${pName}SpeechVolumeLevel") ? "\n└ Volume: (${settings?."${pName}SpeechVolumeLevel"})" : ""
		}
		str += medias ? "${(speaks || echos) ? "\n\n" : "\n"} • Media Players:" : ""
		if(medias) {
			def cnt = 1
			medias?.sort { it?.displayName }?.each { str += it ? "\n│${cnt < medias.size() ? "├" : "└"} $it" : ""; cnt = cnt+1; }
		}
		str += (medias && settings?."${pName}SpeechVolumeLevel") ? "\n├ Volume: (${settings?."${pName}SpeechVolumeLevel"})" : ""
		str += (medias && settings?."${pName}SpeechAllowResume") ? "\n└ Resume: (${strCapitalize(settings?."${pName}SpeechAllowResume")})" : ""
		str += (settings?."${pName}UseCustomSpeechNotifMsg" && (medias || speaks)) ? "\n• Custom Message: (${strCapitalize(settings?."${pName}UseCustomSpeechNotifMsg")})" : ""
	}
	return (str != "") ? "${str}" : null
}
*/
/*
def getAlarmNotifConfigDesc(pName) {
	def str = ""
	if(settings?."${pName}NotifOn" && settings["${pName}AllowAlarmNotif"]) {
		def alarms = getInputToStringDesc(settings["${pName}AlarmDevices"], true)
		str += alarms ? "\n • Alarm Devices:${alarms.size() > 1 ? "\n" : ""}${alarms}" : ""
	}
	return (str != "") ? "${str}" : null
}

def getAlertNotifConfigDesc(pName) {
	def str = ""
//TODO not sure we do all these
	if(settings?."${pName}NotifOn" && (settings["${pName}_Alert_1_Delay"] || settings["${pName}_Alert_2_Delay"]) && (settings["${pName}AllowSpeechNotif"] || settings["${pName}AllowAlarmNotif"])) {
		str += settings["${pName}_Alert_1_Delay"] ? "\nAlert (1) Status:\n  • Delay: (${getEnumValue(longTimeSecEnum(), settings["${pName}_Alert_1_Delay"])})" : ""
//		str += settings["${pName}_Alert_1_Send_Push"] ? "\n  • Send Push: (${settings["${pName}_Alert_1_Send_Push"]})" : ""
//		str += settings["${pName}_Alert_1_Use_Speech"] ? "\n  • Use Speech: (${settings["${pName}_Alert_1_Use_Speech"]})" : ""
		str += settings["${pName}_Alert_1_Use_Alarm"] ? "\n  • Use Alarm: (${settings["${pName}_Alert_1_Use_Alarm"]})" : ""
		str += (settings["${pName}_Alert_1_Use_Alarm"] && settings["${pName}_Alert_1_AlarmType"]) ? "\n ├ Alarm Type: (${getEnumValue(alarmActionsEnum(), settings["${pName}_Alert_1_AlarmType"])})" : ""
		str += (settings["${pName}_Alert_1_Use_Alarm"] && settings["${pName}_Alert_1_Alarm_Runtime"]) ? "\n └ Alarm Runtime: (${getEnumValue(shortTimeEnum(), settings["${pName}_Alert_1_Alarm_Runtime"])})" : ""
		str += settings["${pName}_Alert_2_Delay"] ? "${settings["${pName}_Alert_1_Delay"] ? "\n" : ""}\nAlert (2) Status:\n  • Delay: (${getEnumValue(longTimeSecEnum(), settings["${pName}_Alert_2_Delay"])})" : ""
//		str += settings["${pName}_Alert_2_Send_Push"] ? "\n  • Send Push: (${settings["${pName}_Alert_2_Send_Push"]})" : ""
//		str += settings["${pName}_Alert_2_Use_Speech"] ? "\n  • Use Speech: (${settings["${pName}_Alert_2_Use_Speech"]})" : ""
		str += settings["${pName}_Alert_2_Use_Alarm"] ? "\n  • Use Alarm: (${settings["${pName}_Alert_2_Use_Alarm"]})" : ""
		str += (settings["${pName}_Alert_2_Use_Alarm"] && settings["${pName}_Alert_2_AlarmType"]) ? "\n ├ Alarm Type: (${getEnumValue(alarmActionsEnum(), settings["${pName}_Alert_2_AlarmType"])})" : ""
		str += (settings["${pName}_Alert_2_Use_Alarm"] && settings["${pName}_Alert_2_Alarm_Runtime"]) ? "\n └ Alarm Runtime: (${getEnumValue(shortTimeEnum(), settings["${pName}_Alert_2_Alarm_Runtime"])})" : ""
	}
	return (str != "") ? "${str}" : null
}

def getInputToStringDesc(inpt, addSpace = null) {
	def cnt = 0
	def str = ""
	if(inpt) {
		inpt.sort().each { item ->
			cnt = cnt+1
			str += item ? (((cnt < 1) || (inpt?.size() > 1)) ? "\n    ${item}" : "${addSpace ? "    " : ""}${item}") : ""
		}
	}
	//log.debug "str: $str"
	return (str != "") ? "${str}" : null
}

def isPluralString(obj) {
	return (obj?.size() > 1) ? "(s)" : ""
}

*/
/*
def getRecipientsNames(val) {
	String n = ""
	Integer i = 0
	if(val) {
		//log.debug "val: $val"
		val?.each { r ->
			i = i + 1
			n += i == val?.size() ? "${r}" : "${r},"
		}
	}
	return n?.toString().replaceAll("\\,", "\n")
}

def getRecipientDesc(pName) {
	return (settings?."${pName}NotifPhones" || (settings?."${pName}PushoverEnabled" && settings?."${pName}PushoverDevices")) ? true : false
}
*/

/*

def setDayModeTimePage1(params) {
	def pName = nModePrefix()
	def t0 = ["pName":"${pName}" ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage2(params) {
	def pName = fanCtrlPrefix()
	def t0 = ["pName":"${pName}" ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage3(params) {
	def pName = conWatPrefix()
	def t0 = ["pName":"${pName}" ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage4(params) {
	def pName = humCtrlPrefix()
	def t0 = ["pName":"${pName}" ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage5(params) {
	def pName = extTmpPrefix()
	def t0 = ["pName":"${pName}" ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage(params) {
	def pName = params.pName
	if(params?.pName) {
		state.t_setDayData = params
	} else {
		pName = state?.t_setDayData?.pName
	}
	dynamicPage(name: "setDayModeTimePage", title: "Select Days, Times or Modes", uninstall: false) {
		def secDesc = settings["${pName}DmtInvert"] ? "Not" : "Only"
		def inverted = settings["${pName}DmtInvert"] ? true : false
		section("") {
			def actIcon = settings?."${pName}DmtInvert" ? "inactive" : "active"
			input "${pName}DmtInvert", "bool", title: imgTitle(getAppImg("${actIcon}"), inputTitleStr("${secDesc} in These? (tap to invert)")), defaultValue: false, submitOnChange: true
		}
		section("${secDesc} During these Days, Times, or Modes:") {
			def timeReq = (settings?."${pName}StartTime" || settings."${pName}StopTime") ? true : false
			input "${pName}StartTime", "time", title: imgTitle(getAppImg("start_time_icon.png"), inputTitleStr("Start time")), required: timeReq
			input "${pName}StopTime", "time", title: imgTitle(getAppImg("stop_time_icon.png"), inputTitleStr("Stop time")), required: timeReq
			input "${pName}Days", "enum", title: imgTitle(getAppImg("day_calendar_icon2.png"), inputTitleStr("${inverted ? "Not": "Only"} These Days")), multiple: true, required: false, options: timeDayOfWeekOptions()
			input "${pName}Modes", "mode", title: imgTitle(getAppImg("i_mod"), inputTitleStr("${inverted ? "Not": "Only"} in These Modes")), multiple: true, required: false
		}
		section("Switches:") {
			input "${pName}rstrctSWOn", "capability.switch", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Only execute when these switches are all ON")), multiple: true, required: false
			input "${pName}rstrctSWOff", "capability.switch", title: imgTitle(getAppImg("switch_off_icon.png"), inputTitleStr("Only execute when these switches are all OFF")), multiple: true, required: false
		}
	}
}

def getDayModeTimeDesc(pName) {
	def startTime = settings?."${pName}StartTime"
	def stopTime = settings?."${pName}StopTime"
	def dayInput = settings?."${pName}Days"
	def modeInput = settings?."${pName}Modes"
	def inverted = settings?."${pName}DmtInvert" ?: null
	def swOnInput = settings?."${pName}rstrctSWOn"
	def swOffInput = settings?."${pName}rstrctSWOff"
	def str = ""
	def days = getInputToStringDesc(dayInput)
	def modes = getInputToStringDesc(modeInput)
	def swOn = getInputToStringDesc(swOnInput)
	def swOff = getInputToStringDesc(swOffInput)
	str += ((startTime && stopTime) || modes || days) ? "${!inverted ? "When" : "When Not"}:" : ""
	str += (startTime && stopTime) ? "\n • Time: ${time2Str(settings?."${pName}StartTime")} - ${time2Str(settings?."${pName}StopTime")}" : ""
	str += days ? "${(startTime && stopTime) ? "\n" : ""}\n • Day${isPluralString(dayInput)}: ${days}" : ""
	str += modes ? "${((startTime && stopTime) || days) ? "\n" : ""}\n • Mode${isPluralString(modeInput)}: ${modes}" : ""
	str += swOn ? "${((startTime && stopTime) || days || modes) ? "\n" : ""}\n • Switch${isPluralString(swOnInput)} that must be on: ${getRestSwitch(swOnInput)}" : ""
	str += swOff ? "${((startTime && stopTime) || days || modes || swOn) ? "\n" : ""}\n • Switch${isPluralString(swOffInput)} that must be off: ${getRestSwitch(swOffInput)}" : ""
	str += (str != "") ? descriptions("d_ttm") : ""
	return str
}

def getRestSwitch(swlist) {
	def swDesc = ""
	def swCnt = 0
	def rmSwCnt = swlist?.size() ?: 0
	swlist?.sort { it?.displayName }?.each { sw ->
		swCnt = swCnt+1
		swDesc += "${swCnt >= 1 ? "${swCnt == rmSwCnt ? "\n   └" : "\n   ├"}" : "\n   └"} ${sw?.label}: (${strCapitalize(sw?.currentSwitch)})"
	}
	return (swDesc == "") ? null : "${swDesc}"
}

def getDmtSectionDesc(autoType) {
	return settings["${autoType}DmtInvert"] ? "Do Not Act During these Days, Times, or Modes:" : "Only Act During these Days, Times, or Modes:"
//TODO add switches to adjust schedule
}
*/
/************************************************************************************************
|				AUTOMATION SCHEDULE CHECK								|
*************************************************************************************************/
/*
def autoScheduleOk(autoType) {
	try {
		def inverted = settings?."${autoType}DmtInvert" ? true : false
		def modeOk = true
		modeOk = (!settings?."${autoType}Modes" || ((isInMode(settings?."${autoType}Modes") && !inverted) || (!isInMode(settings?."${autoType}Modes") && inverted))) ? true : false

		//dayOk
		def dayOk = true
		def dayFmt = new SimpleDateFormat("EEEE")
		dayFmt.setTimeZone(getTimeZone())
		def today = dayFmt.format(new Date())
		def inDay = (today in settings?."${autoType}Days") ? true : false
		dayOk = (!settings?."${autoType}Days" || ((inDay && !inverted) || (!inDay && inverted))) ? true : false

		//scheduleTimeOk
		def timeOk = true
		if(settings?."${autoType}StartTime" && settings?."${autoType}StopTime") {
			def inTime = (timeOfDayIsBetween(settings?."${autoType}StartTime", settings?."${autoType}StopTime", new Date(), getTimeZone())) ? true : false
			timeOk = ((inTime && !inverted) || (!inTime && inverted)) ? true : false
		}

		def soFarOk = (modeOk && dayOk && timeOk) ? true : false
		def swOk = true
		if(soFarOk && settings?."${autoType}rstrctSWOn") {
			for(sw in settings["${autoType}rstrctSWOn"]) {
				if (sw.currentValue("switch") != "on") {
					swOk = false
					break
				}
			}
		}
		soFarOk = (modeOk && dayOk && timeOk && swOk) ? true : false
		if(soFarOk && settings?."${autoType}rstrctSWOff") {
			for(sw in settings["${autoType}rstrctSWOff"]) {
				if (sw.currentValue("switch") != "off") {
					swOk = false
					break
				}
			}
		}

		LogAction("autoScheduleOk( dayOk: $dayOk | modeOk: $modeOk | dayOk: ${dayOk} | timeOk: $timeOk | swOk: $swOk | inverted: ${inverted})", "info", false)
		return (modeOk && dayOk && timeOk && swOk) ? true : false
	} catch (ex) {
		log.error "${autoType}-autoScheduleOk Exception:", ex
		//parent?.sendExceptionData(ex, "autoScheduleOk", true, getAutoType())
	}
}
*/

/************************************************************************************************
|						SEND NOTIFICATIONS VIA PARENT APP								|
*************************************************************************************************/
def sendNofificationMsg(msg, msgType, pName, lvl=null, pusho=null, sms=null) {
	LogAction("sendNofificationMsg($msg, $msgType, $pName, $sms, $pusho)", "debug", false)
	if(settings?."${pName}NotifOn" == true) {
		def nlvl = lvl ?: (sms || pusho) ? 5 : 4
		if(settings?."${pName}UseMgrNotif" == false) {
			def mySms = sms ?: settings?."${pName}NotifPhones"
			if(mySms) {
				parent?.sendMsg(msgType, msg, nlvl,  null, mySms)
			}
			if(pusho && settings?."${pName}PushoverDevices") {
				parent?.sendMsg(msgType, msg, nlvl, settings?."${pName}PushoverDevices")
			}
		} else {
			parent?.sendMsg(msgType, msg, nlvl)
		}
	} else {
		LogAction("sendMsg: Message Skipped as notifications off ($msg)", "info", true)
	}
}

/************************************************************************************************
|							GLOBAL Code | Logging AND Diagnostic							|
*************************************************************************************************/
def sendEventPushNotifications(message, type, pName) {
	LogTrace("sendEventPushNotifications($message, $type, $pName)")
	sendNofificationMsg(message, type, pName)
}

/*
def sendEventVoiceNotifications(vMsg, pName, msgId, rmAAMsg=false, rmMsgId) {
	def allowNotif = settings?."${pName}NotifOn" ? true : false
	def allowSpeech = allowNotif && settings?."${pName}AllowSpeechNotif" ? true : false
	def ok2Notify = setting?."${pName}UseParentNotifRestrictions" != false ? getOk2Notify(pName) : getOk2Notify(pName) //parent?.getOk2Notify()

	LogAction("sendEventVoiceNotifications($vMsg, $pName) | ok2Notify: $ok2Notify", "info", false)
	if(allowNotif && allowSpeech) {
		if(ok2Notify && (settings["${pName}SpeechDevices"] || settings["${pName}SpeechMediaPlayer"] || settings["${pName}EchoDevices"])) {
			sendTTS(vMsg, pName)
		}
		if(settings["${pName}SendToAskAlexaQueue"]) {		// we queue to Alexa regardless of quiet times
			if(rmMsgId != null && rmAAMsg == true) {
				removeAskAlexaQueueMsg(rmMsgId)
			}
			if (vMsg && msgId != null) {
				addEventToAskAlexaQueue(vMsg, msgId)
			}
		}
	}
}
*/

/*
def addEventToAskAlexaQueue(vMsg, msgId, queue=null) {
	if(false) { //parent?.getAskAlexaMQEn() == true) {
		if(parent.getAskAlexaMultiQueueEn()) {
			LogAction("sendEventToAskAlexaQueue: Adding this Message to the Ask Alexa Queue ($queues): ($vMsg)|${msgId}", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueue", value: "${app?.label}", isStateChange: true, descriptionText: "${vMsg}", unit: "${msgId}", data:queues)
		} else {
			LogAction("sendEventToAskAlexaQueue: Adding this Message to the Ask Alexa Queue: ($vMsg)|${msgId}", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueue", value: "${app?.label}", isStateChange: true, descriptionText: "${vMsg}", unit: "${msgId}")
		}
	}
}

def removeAskAlexaQueueMsg(msgId, queue=null) {
	if(false) { //parent?.getAskAlexaMQEn() == true) {
		if(parent.getAskAlexaMultiQueueEn()) {
			LogAction("removeAskAlexaQueueMsg: Removing Message ID (${msgId}) from the Ask Alexa Queue ($queues)", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueueDelete", value: "${app?.label}", isStateChange: true, unit: msgId, data: queues)
		} else {
			LogAction("removeAskAlexaQueueMsg: Removing Message ID (${msgId}) from the Ask Alexa Queue", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueueDelete", value: "${app?.label}", isStateChange: true, unit: msgId)
		}
	}
}
*/

/*
def scheduleAlarmOn(autoType) {
	LogAction("scheduleAlarmOn: autoType: $autoType a1DelayVal: ${getAlert1DelayVal(autoType)}", "debug", false)
	def timeVal = getAlert1DelayVal(autoType).toInteger()
	def ok2Notify = true //setting?."${autoType}UseParentNotifRestrictions" != false ? getOk2Notify(autoType) : getOk2Notify(autoType) //parent?.getOk2Notify()

	LogAction("scheduleAlarmOn timeVal: $timeVal ok2Notify: $ok2Notify", "info", false)
	if(canSchedule() && ok2Notify) {
		if(timeVal > 0) {
			runIn(timeVal, "alarm0FollowUp", [data: [autoType: autoType]])
			LogAction("scheduleAlarmOn: Scheduling Alarm Followup 0 in timeVal: $timeVal", "info", false)
			state."${autoType}AlarmActive" = true
		} else { LogAction("scheduleAlarmOn: Did not schedule ANY operation timeVal: $timeVal", "error", true) }
	} else { LogAction("scheduleAlarmOn: Could not schedule operation timeVal: $timeVal", "error", true) }
}

def alarm0FollowUp(val) {
	def autoType = val.autoType
	LogAction("alarm0FollowUp: autoType: $autoType 1 OffVal: ${getAlert1AlarmEvtOffVal(autoType)}", "debug", false)
	def timeVal = getAlert1AlarmEvtOffVal(autoType).toInteger()
	LogAction("alarm0FollowUp timeVal: $timeVal", "info", false)
	if(canSchedule() && timeVal > 0 && sendEventAlarmAction(1, autoType)) {
		runIn(timeVal, "alarm1FollowUp", [data: [autoType: autoType]])
		LogAction("alarm0FollowUp: Scheduling Alarm Followup 1 in timeVal: $timeVal", "info", false)
	} else { LogAction ("alarm0FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

def alarm1FollowUp(val) {
	def autoType = val.autoType
	LogAction("alarm1FollowUp autoType: $autoType a2DelayVal: ${getAlert2DelayVal(autoType)}", "debug", false)
	def aDev = settings["${autoType}AlarmDevices"]
	if(aDev) {
		aDev?.off()
		storeLastAction("Set Alarm OFF", getDtNow())
		LogAction("alarm1FollowUp: Turning OFF ${aDev}", "info", false)
	}
	def timeVal = getAlert2DelayVal(autoType).toInteger()
	//if(canSchedule() && (settings["${autoType}_Alert_2_Use_Alarm"] && timeVal > 0)) {
	if(canSchedule() && timeVal > 0) {
		runIn(timeVal, "alarm2FollowUp", [data: [autoType: autoType]])
		LogAction("alarm1FollowUp: Scheduling Alarm Followup 2 in timeVal: $timeVal", "info", false)
	} else { LogAction ("alarm1FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

def alarm2FollowUp(val) {
	def autoType = val.autoType
	LogAction("alarm2FollowUp: autoType: $autoType 2 OffVal: ${getAlert2AlarmEvtOffVal(autoType)}", "debug", false)
	def timeVal = getAlert2AlarmEvtOffVal(autoType)
	if(canSchedule() && timeVal > 0 && sendEventAlarmAction(2, autoType)) {
		runIn(timeVal, "alarm3FollowUp", [data: [autoType: autoType]])
		LogAction("alarm2FollowUp: Scheduling Alarm Followup 3 in timeVal: $timeVal", "info", false)
	} else { LogAction ("alarm2FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

def alarm3FollowUp(val) {
	def autoType = val.autoType
	LogAction("alarm3FollowUp: autoType: $autoType", "debug", false)
	def aDev = settings["${autoType}AlarmDevices"]
	if(aDev) {
		aDev?.off()
		storeLastAction("Set Alarm OFF", getDtNow())
		LogAction("alarm3FollowUp: Turning OFF ${aDev}", "info", false)
	}
	state."${autoType}AlarmActive" = false
}

def alarmEvtSchedCleanup(autoType) {
	if(state?."${autoType}AlarmActive") {
		LogAction("Cleaning Up Alarm Event Schedules autoType: $autoType", "info", false)
		def items = ["alarm0FollowUp","alarm1FollowUp", "alarm2FollowUp", "alarm3FollowUp"]
		items.each {
			unschedule("$it")
		}
		def val = [ autoType: autoType ]
		alarm3FollowUp(val)
	}
}

def sendEventAlarmAction(evtNum, autoType) {
	LogAction("sendEventAlarmAction evtNum: $evtNum autoType: $autoType", "info", false)
	try {
		def resval = false
		def allowNotif = settings?."${autoType}NotifOn" ? true : false
		def allowAlarm = allowNotif && settings?."${autoType}AllowAlarmNotif" ? true : false
		def aDev = settings["${autoType}AlarmDevices"]
		if(allowNotif && allowAlarm && aDev) {
			//if(settings["${autoType}_Alert_${evtNum}_Use_Alarm"]) {
				resval = true
				def alarmType = settings["${autoType}_Alert_${evtNum}_AlarmType"].toString()
				switch (alarmType) {
					case "both":
						state?."${autoType}alarmEvt${evtNum}StartDt" = getDtNow()
						aDev?.both()
						storeLastAction("Set Alarm BOTH ON", getDtNow(), autoType)
						break
					case "siren":
						state?."${autoType}alarmEvt${evtNum}StartDt" = getDtNow()
						aDev?.siren()
						storeLastAction("Set Alarm SIREN ON", getDtNow(), autoType)
						break
					case "strobe":
						state?."${autoType}alarmEvt${evtNum}StartDt" = getDtNow()
						aDev?.strobe()
						storeLastAction("Set Alarm STROBE ON", getDtNow(), autoType)
						break
					default:
						resval = false
						break
				}
			//}
		}
	} catch (ex) {
		log.error "sendEventAlarmAction Exception: ($evtNum) - ", ex
		//parent?.sendExceptionData(ex, "sendEventAlarmAction", true, getAutoType())
	}
	return resval
}

def alarmAlertEvt(evt) {
	LogAction("alarmAlertEvt: ${evt.displayName} Alarm State is Now (${evt.value})", "debug", false)
}

def getAlert1DelayVal(autoType) { return !settings["${autoType}_Alert_1_Delay"] ? 300 : (settings["${autoType}_Alert_1_Delay"].toInteger()) }
def getAlert2DelayVal(autoType) { return !settings["${autoType}_Alert_2_Delay"] ? 300 : (settings["${autoType}_Alert_2_Delay"].toInteger()) }

def getAlert1AlarmEvtOffVal(autoType) { return !settings["${autoType}_Alert_1_Alarm_Runtime"] ? 10 : (settings["${autoType}_Alert_1_Alarm_Runtime"].toInteger()) }
def getAlert2AlarmEvtOffVal(autoType) { return !settings["${autoType}_Alert_2_Alarm_Runtime"] ? 10 : (settings["${autoType}_Alert_2_Alarm_Runtime"].toInteger()) }
*/

/*
def getAlarmEvt1RuntimeDtSec() { return !state?.alarmEvt1StartDt ? 100000 : GetTimeDiffSeconds(state?.alarmEvt1StartDt).toInteger() }
def getAlarmEvt2RuntimeDtSec() { return !state?.alarmEvt2StartDt ? 100000 : GetTimeDiffSeconds(state?.alarmEvt2StartDt).toInteger() }
*/

/*
void sendTTS(txt, pName) {
	LogAction("sendTTS(data: ${txt})", "debug", false)
	try {
		def msg = txt?.toString()?.replaceAll("\\[|\\]|\\(|\\)|\\'|\\_", "")
		def spks = settings?."${pName}SpeechDevices"
		def meds = settings?."${pName}SpeechMediaPlayer"
		def echos = settings?."${pName}EchoDevices"
		def res = settings?."${pName}SpeechAllowResume"
		def vol = settings?."${pName}SpeechVolumeLevel"
		LogAction("sendTTS msg: $msg | speaks: $spks | medias: $meds | echos: $echos| resume: $res | volume: $vol", "debug", false)
		if(settings?."${pName}AllowSpeechNotif") {
			if(spks) {
				spks*.speak(msg)
			}
			if(meds) {
				meds?.each {
					if(res) {
						def currentStatus = it.latestValue('status')
						def currentTrack = it.latestState("trackData")?.jsonValue
						def currentVolume = it.latestState("level")?.integerValue ? it.currentState("level")?.integerValue : 0
						if(vol) {
							it?.playTextAndResume(msg, vol?.toInteger())
						} else {
							it?.playTextAndResume(msg)
						}
					}
					else {
						it?.playText(msg)
					}
				}
			}
			if(echos) {
				echos*.setVolumeAndSpeak(settings?."${pName}SpeechVolumeLevel", msg as String)
			}
		}
	} catch (ex) {
		log.error "sendTTS Exception:", ex
		//parent?.sendExceptionData(ex, "sendTTS", true, getAutoType())
	}
}
*/
/*

def scheduleTimeoutRestore(pName) {
	def timeOutVal = settings["${pName}OffTimeout"]?.toInteger()
	if(timeOutVal && !state?."${pName}TimeoutScheduled") {
		runIn(timeOutVal.toInteger(), "restoreAfterTimeOut", [data: [pName:pName]])
		LogAction("Mode Restoration Timeout Scheduled ${pName} (${getEnumValue(longTimeSecEnum(), settings?."${pName}OffTimeout")})", "info", true)
		state."${pName}TimeoutScheduled" = true
	}
}

def unschedTimeoutRestore(pName) {
	def timeOutVal = settings["${pName}OffTimeout"]?.toInteger()
	if(timeOutVal && state?."${pName}TimeoutScheduled") {
		unschedule("restoreAfterTimeOut")
		LogAction("Cancelled Scheduled Mode Restoration Timeout ${pName}", "info", false)
	}
	state."${pName}TimeoutScheduled" = false
}

def restoreAfterTimeOut(val) {
	def pName = val?.pName.value
	if(pName && settings?."${pName}OffTimeout") {
		switch(pName) {
			case "conWat":
				state."${pName}TimeoutScheduled" = false
				conWatCheck(true)
				break
			//case "leakWat":
				//leakWatCheck(true)
				//break
			case "extTmp":
				state."${pName}TimeoutScheduled" = false
				extTmpTempCheck(true)
				break
			default:
				LogAction("restoreAfterTimeOut no pName match ${pName}", "error", true)
				break
		}
	}
}

def checkThermostatDupe(tstatOne, tstatTwo) {
	def result = false
	if(tstatOne && tstatTwo) {
		def pTstat = tstatOne?.deviceNetworkId.toString()
		def mTstatAr = []
		tstatTwo?.each { ts ->
			mTstatAr << ts?.deviceNetworkId.toString()
		}
		if(pTstat in mTstatAr) { return true }
	}
	return result
}

def checkModeDuplication(modeOne, modeTwo) {
	def result = false
	if(modeOne && modeTwo) {
		modeOne?.each { dm ->
			if(dm in modeTwo) {
				result = true
			}
		}
	}
	return result
}

private getDeviceSupportedCommands(dev) {
	return dev?.supportedCommands.findAll { it as String }
}

def checkFanSpeedSupport(dev) {
	def req = ["lowSpeed", "medSpeed", "highSpeed"]
	def devCnt = 0
	def devData = getDeviceSupportedCommands(dev)
	devData.each { cmd ->
		if(cmd.name in req) { devCnt = devCnt+1 }
	}
	def t0 = dev?.currentValue("currentState")
	def speed = t0 ?: null
	//log.debug "checkFanSpeedSupport (speed: $speed | devCnt: $devCnt)"
	return (speed && devCnt == 3) ? true : false
}

def getTstatCapabilities(tstat, autoType, dyn = false) {
	try {
		def canCool = true
		def canHeat = true
		def hasFan = true
		if(tstat?.currentCanCool) { canCool = tstat?.currentCanCool.toBoolean() }
		if(tstat?.currentCanHeat) { canHeat = tstat?.currentCanHeat.toBoolean() }
		if(tstat?.currentHasFan) { hasFan = tstat?.currentHasFan.toBoolean() }

		state?."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatCanCool" = canCool
		state?."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatCanHeat" = canHeat
		state?."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatHasFan" = hasFan
	} catch (ex) {
		log.error "getTstatCapabilities Exception:", ex
		//parent?.sendExceptionData(ex, "getTstatCapabilities", true, getAutoType())
	}
}

def getSafetyTemps(tstat, usedefault=true) {
	def minTemp = tstat?.currentState("safetyTempMin")?.doubleValue
	def maxTemp = tstat?.currentState("safetyTempMax")?.doubleValue
	if(minTemp == 0) {
		if(usedefault) { minTemp = (getTemperatureScale() == "C") ? 7 : 45 }
		else { minTemp = null }
	}
	if(maxTemp == 0) { maxTemp = null }
	if(minTemp || maxTemp) {
		return ["min":minTemp, "max":maxTemp]
	}
	return null
}

def getComfortDewpoint(tstat, usedefault=true) {
	def maxDew = tstat?.currentState("comfortDewpointMax")?.doubleValue
	maxDew = maxDew ?: 0.0
	if(maxDew == 0.0) {
		if(usedefault) {
			maxDew = (getTemperatureScale() == "C") ? 19 : 66
			return maxDew.toDouble()
		}
		return null
	}
	return maxDew
}

def getSafetyTempsOk(tstat) {
	def sTemps = getSafetyTemps(tstat)
	//log.debug "sTempsOk: $sTemps"
	if(sTemps) {
		def curTemp = tstat?.currentTemperature?.toDouble()
		//log.debug "curTemp: ${curTemp}"
		if( ((sTemps?.min != null && sTemps?.min.toDouble() != 0) && (curTemp < sTemps?.min.toDouble())) || ((sTemps?.max != null && sTemps?.max?.toDouble() != 0) && (curTemp > sTemps?.max?.toDouble())) ) {
			return false
		}
	} // else { log.debug "getSafetyTempsOk: no safety Temps" }
	return true
}

def getGlobalDesiredHeatTemp() {
	Double t0 = null //parent?.settings?.locDesiredHeatTemp?.toDouble()
	return t0 ?: null
}

def getGlobalDesiredCoolTemp() {
	Double t0 = null // parent?.settings?.locDesiredCoolTemp?.toDouble()
	return t0 ?: null
}

def getClosedContacts(contacts) {
	if(contacts) {
		def cnts = contacts?.findAll { it?.currentContact == "closed" }
		return cnts ?: null
	}
	return null
}

def getOpenContacts(contacts) {
	if(contacts) {
		def cnts = contacts?.findAll { it?.currentContact == "open" }
		return cnts ?: null
	}
	return null
}

def getDryWaterSensors(sensors) {
	if(sensors) {
		def cnts = sensors?.findAll { it?.currentWater == "dry" }
		return cnts ?: null
	}
	return null
}

def getWetWaterSensors(sensors) {
	if(sensors) {
		def cnts = sensors?.findAll { it?.currentWater == "wet" }
		return cnts ?: null
	}
	return null
}

def isContactOpen(con) {
	def res = false
	if(con) {
		if(con?.currentSwitch == "on") { res = true }
	}
	return res
}

def isSwitchOn(dev) {
	def res = false
	if(dev) {
		dev?.each { d ->
			if(d?.currentSwitch == "on") { res = true }
		}
	}
	return res
}

def isPresenceHome(presSensor) {
	def res = false
	if(presSensor) {
		presSensor?.each { d ->
			if(d?.currentPresence == "present") { res = true }
		}
	}
	return res
}

def isSomebodyHome(sensors) {
	if(sensors) {
		def cnts = sensors?.findAll { it?.currentPresence == "present" }
		return cnts ? true : false
	}
	return false
}

def getTstatPresence(tstat) {
	def pres = "not present"
	if(tstat) { pres = tstat?.currentPresence }
	return pres
}

def setTstatMode(tstat, mode, autoType=null) {
	def result = false
	if(mode && tstat) {
		def curMode = tstat?.currentThermostatMode?.toString()
		if (curMode != mode) {
			try {
				if(mode == "auto") { tstat.auto(); result = true }
				else if(mode == "heat") { tstat.heat(); result = true }
				else if(mode == "cool") { tstat.cool(); result = true }
				else if(mode == "off") { tstat.off(); result = true }
				else {
					if(mode == "eco") {
						tstat.eco(); result = true
						LogTrace("setTstatMode mode action | type: $autoType")
//						if(autoType) { sendEcoActionDescToDevice(tstat, autoType) } // THIS ONLY WORKS ON NEST THERMOSTATS
					}
				}
			}
			catch (ex) {
				log.error "setTstatMode() Exception: ${tstat?.label} does not support mode ${mode}; check IDE and install instructions", ex
				//parent?.sendExceptionData(ex, "setTstatMode", true, getAutoType())
			}
		}

		if(result) { LogAction("setTstatMode: '${tstat?.label}' Mode set to (${strCapitalize(mode)})", "info", false) }
		else { LogAction("setTstatMode() | No Mode change: ${mode}", "info", false) }
	} else {
		LogAction("setTstatMode() | Invalid or Missing Mode received: ${mode}", "warn", true)
	}
	return result
}

def setMultipleTstatMode(tstats, mode, autoType=null) {
	def result = false
	if(tstats && mode) {
		tstats?.each { ts ->
			def retval
//			try {
				retval = setTstatMode(ts, mode, autoType)
//			} catch (ex) {
//				log.error "setMultipleTstatMode() Exception:", ex
//				parent?.sendExceptionData(ex, "setMultipleTstatMode", true, getAutoType())
//			}

			if(retval) {
				LogAction("Setting ${ts?.displayName} Mode to (${mode})", "info", false)
				storeLastAction("Set ${ts?.displayName} to (${mode})", getDtNow(), autoType)
				result = true
			} else {
				LogAction("Failed Setting ${ts} Mode to (${mode})", "warn", true)
				return false
			}
		}
	} else {
		LogAction("setMultipleTstatMode(${tstats}, $mode, $autoType) | Invalid or Missing tstats or Mode received: ${mode}", "warn", true)
	}
	return result
}

def setTstatAutoTemps(tstat, coolSetpoint, heatSetpoint, pName, mir=null) {

	def retVal = false
	def setStr = "No thermostat device"
	def tStr = "setTstatAutoTemps: [tstat: ${tstat?.displayName} | Mode: ${hvacMode} | coolSetpoint: ${coolSetpoint}${tempScaleStr} | heatSetpoint: ${heatSetpoint}${tempScaleStr}] "
	def heatFirst
	def setHeat
	def setCool
	def hvacMode = "unknown"
	def reqCool
	def reqHeat
	def curCoolSetpoint
	def curHeatSetpoint
	def tempScaleStr = "${tUnitStr()}"

	if(tstat) {
		hvacMode = tstat?.currentThermostatMode.toString()
		LogTrace(tStr)

		retVal = true
		setStr = "Error: "

		curCoolSetpoint = getTstatSetpoint(tstat, "cool")
		curHeatSetpoint = getTstatSetpoint(tstat, "heat")
		def diff = getTemperatureScale() == "C" ? 2.0 : 3.0
		reqCool = coolSetpoint?.toDouble() ?: null
		reqHeat = heatSetpoint?.toDouble() ?: null

		if(!reqCool && !reqHeat) { retVal = false; setStr += "Missing COOL and HEAT Setpoints" }

		if(hvacMode in ["auto"]) {
			if(!reqCool && reqHeat) { reqCool = (double) ((curCoolSetpoint > (reqHeat + diff)) ? curCoolSetpoint : (reqHeat + diff)) }
			if(!reqHeat && reqCool) { reqHeat = (double) ((curHeatSetpoint < (reqCool - diff)) ? curHeatSetpoint : (reqCool - diff)) }
			if((reqCool && reqHeat) && (reqCool >= (reqHeat + diff))) {
				if(reqHeat <= curHeatSetpoint) { heatFirst = true }
					else if(reqCool >= curCoolSetpoint) { heatFirst = false }
					else if(reqHeat > curHeatSetpoint) { heatFirst = false }
					else { heatFirst = true }
				if(heatFirst) {
					if(reqHeat != curHeatSetpoint) { setHeat = true }
					if(reqCool != curCoolSetpoint) { setCool = true }
				} else {
					if(reqCool != curCoolSetpoint) { setCool = true }
					if(reqHeat != curHeatSetpoint) { setHeat = true }
				}
			} else {
				setStr += " or COOL/HEAT is not separated by ${diff}"
				retVal = false
			}

		} else if(hvacMode in ["cool"] && reqCool) {
			if(reqCool != curCoolSetpoint) { setCool = true }

		} else if(hvacMode in ["heat"] && reqHeat) {
			if(reqHeat != curHeatSetpoint) { setHeat = true }

		} else {
			setStr += "incorrect HVAC Mode (${hvacMode}"
			retVal = false
		}
	}
	if(retVal) {
		setStr = "Setting: "
		if(heatFirst && setHeat) {
			setStr += "heatSetpoint: (${reqHeat}${tempScaleStr}) "
			if(reqHeat != curHeatSetpoint) {
				tstat?.setHeatingSetpoint(reqHeat)
				storeLastAction("Set ${tstat} Heat Setpoint ${reqHeat}${tempScaleStr}", getDtNow(), pName, tstat)
				if(mir) { mir*.setHeatingSetpoint(reqHeat) }
			}
		}
		if(setCool) {
			setStr += "coolSetpoint: (${reqCool}${tempScaleStr}) "
			if(reqCool != curCoolSetpoint) {
				tstat?.setCoolingSetpoint(reqCool)
				storeLastAction("Set ${tstat} Cool Setpoint ${reqCool}", getDtNow(), pName, tstat)
				if(mir) { mir*.setCoolingSetpoint(reqCool) }
			}
		}
		if(!heatFirst && setHeat) {
			setStr += "heatSetpoint: (${reqHeat}${tempScaleStr})"
			if(reqHeat != curHeatSetpoint) {
				tstat?.setHeatingSetpoint(reqHeat)
				storeLastAction("Set ${tstat} Heat Setpoint ${reqHeat}${tempScaleStr}", getDtNow(), pName, tstat)
				if(mir) { mir*.setHeatingSetpoint(reqHeat) }
			}
		}
		//LogAction("setTstatAutoTemps() | Setting tstat [${tstat?.displayName} | mode: (${hvacMode}) | ${setStr}]", "info", false)
	} else {
		//LogAction("setTstatAutoTemps() | Setting tstat [${tstat?.displayName} | mode: (${hvacMode}) | ${setStr}]", "warn", true)
	}
	LogAction(tStr + setStr, retVal ? "info" : "warn", true)
	//LogAction("setTstatAutoTemps() | Setting tstat [${tstat?.displayName} | mode: (${hvacMode}) | ${setStr}]", retVal ? "info" : "warn", true)
	return retVal
}

*/

/******************************************************************************
*					Keep These Methods						*
*******************************************************************************/
/*
def switchEnumVals() { return [0:"Off", 1:"On", 2:"On/Off"] }

def longTimeMinEnum() {
	def vals = [
		1:"1 Minute", 2:"2 Minutes", 3:"3 Minutes", 4:"4 Minutes", 5:"5 Minutes", 10:"10 Minutes", 15:"15 Minutes", 20:"20 Minutes", 25:"25 Minutes", 30:"30 Minutes",
		45:"45 Minutes", 60:"1 Hour", 120:"2 Hours", 240:"4 Hours", 360:"6 Hours", 720:"12 Hours", 1440:"24 Hours"
	]
	return vals
}

def fanTimeSecEnum() {
	def vals = [
		60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes"
	]
	return vals
}
*/

def longTimeSecEnum() {
	def vals = [
		0:"Off", 60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 2700:"45 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 10:"10 Seconds(Testing)"
	]
	return vals
}

def shortTimeEnum() {
	def vals = [
		1:"1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds",
		8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds", 15:"15 Seconds", 30:"30 Seconds", 60:"60 Seconds"
	]
	return vals
}
/*
def switchRunEnum(addAlways = false) {
	def pName = schMotPrefix()
	def hasFan = state?."${pName}TstatHasFan" ? true : false
	def canCool = state?."${pName}TstatCanCool" ? true : false
	def canHeat = state?."${pName}TstatCanHeat" ? true : false
	def vals = [ 1:"Any operation: Heating or Cooling" ]
	if(hasFan) {
		vals << [2:"With HVAC Fan Only"]
	}
	if(canHeat) {
		vals << [3:"Heating"]
	}
	if(canCool) {
		vals << [4:"Cooling"]
	}
	if(addAlways) {
		vals << [5:"Any Operating or non-operating State"]
	}
	return vals
}

def fanModeTrigEnum() {
	def pName = schMotPrefix()
	def canCool = state?."${pName}TstatCanCool" ? true : false
	def canHeat = state?."${pName}TstatCanHeat" ? true : false
	def hasFan = state?."${pName}TstatHasFan" ? true : false
	def vals = ["auto":"Auto", "cool":"Cool", "heat":"Heat", "eco":"Eco", "any":"Any Mode"]
	if(!canHeat) {
		vals = ["cool":"Cool", "eco":"Eco", "any":"Any Mode"]
	}
	if(!canCool) {
		vals = ["heat":"Heat", "eco":"Eco", "any":"Any Mode"]
	}
	return vals
}

def tModeHvacEnum(canHeat, canCool, canRtn=null) {
	def vals = ["auto":"Auto", "cool":"Cool", "heat":"Heat", "eco":"Eco"]
	if(!canHeat) {
		vals = ["cool":"Cool", "eco":"Eco"]
	}
	if(!canCool) {
		vals = ["heat":"Heat", "eco":"Eco"]
	}
	if(canRtn) {
		vals << ["rtnFromEco":"Return from ECO if in ECO"]
	}
	return vals
}

def alarmActionsEnum() {
	def vals = ["siren":"Siren", "strobe":"Strobe", "both":"Both (Siren/Strobe)"]
	return vals
}
*/

def getEnumValue(enumName, inputName) {
	def result = "unknown"
	def resultList = []
	def inputIsList = getObjType(inputName) == "List" ? true : false
	if(enumName) {
		enumName?.each { item ->
			if(inputIsList) {
				inputName?.each { inp ->
					if(item?.key.toString() == inp?.toString()) {
						resultList.push(item?.value)
					}
				}
			} else
			if(item?.key.toString() == inputName?.toString()) {
				result = item?.value
			}
		}
	}
	if(inputIsList) {
		return resultList
	} else {
		return result
	}
}

/*
def getSunTimeState() {
	def tz = TimeZone.getTimeZone(location.timeZone.ID)
	def sunsetTm = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", location?.currentValue('sunsetTime')).format('h:mm a', tz)
	def sunriseTm = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", location?.currentValue('sunriseTime')).format('h:mm a', tz)
	state.sunsetTm = sunsetTm
	state.sunriseTm = sunriseTm
}

def parseDt(format, dt) {
	def result
	def newDt = Date.parse("$format", dt)
	result = formatDt(newDt)
	//log.debug "result: $result"
	return result
}
*/


/******************************************************************************
*								STATIC METHODS								*
*******************************************************************************/

//def getAutoAppChildName()	{ return "Nest Automations" }
//def getWatDogAppChildName()	{ return "Nest Location ${location.name} Watchdog" }

//def getChildName(str)		{ return "${str}" }

//def getChildAppVer(appName) { return appName?.appVersion() ? "v${appName?.appVersion()}" : "" }

//def getUse24Time()			{ return useMilitaryTime ? true : false }

//Returns app State Info
def getStateSize() {
	def resultJson = new groovy.json.JsonOutput().toJson(state)
	return resultJson?.toString().length()
}
def getStateSizePerc()		{ return (int) ((stateSize / 100000)*100).toDouble().round(0) }

def getLocationModes() {
	def result = []
	location?.modes.sort().each {
		if(it) { result.push("${it}") }
	}
	return result
}

def getObjType(obj) {
	if(obj instanceof String) {return "String"}
	else if(obj instanceof Map) {return "Map"}
	else if(obj instanceof List) {return "List"}
	else if(obj instanceof ArrayList) {return "ArrayList"}
	else if(obj instanceof Integer) {return "Int"}
	else if(obj instanceof BigInteger) {return "BigInt"}
	else if(obj instanceof Long) {return "Long"}
	else if(obj instanceof Boolean) {return "Bool"}
	else if(obj instanceof BigDecimal) {return "BigDec"}
	else if(obj instanceof Float) {return "Float"}
	else if(obj instanceof Byte) {return "Byte"}
	else { return "unknown"}
}

//def preStrObj() { [1:"•", 2:"│", 3:"├", 4:"└", 5:"    "] }

def getTimeZone() {
	def tz = null
	if(location?.timeZone) { tz = location?.timeZone }
	//else { tz = getNestTimeZone() ? TimeZone.getTimeZone(getNestTimeZone()) : null }
	if(!tz) { LogAction("getTimeZone: Hub or Nest TimeZone not found", "warn", true) }
	return tz
}

def formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("HE TimeZone is not set; Please open your location and Press Save", "warn", true)
	}
	return tf.format(dt)
}

/*
def getGlobTitleStr(typ) {
	return "Desired Default ${typ} Temp (${tUnitStr()})"
}
*/

def formatDt2(tm) {
	//def formatVal = settings?.useMilitaryTime ? "MMM d, yyyy - HH:mm:ss" : "MMM d, yyyy - h:mm:ss a"
	def formatVal = "MMM d, yyyy - h:mm:ss a"
	def tf = new SimpleDateFormat(formatVal)
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	return tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", tm.toString()))
}

def tUnitStr() {
	return "\u00b0${getTemperatureScale()}"
}

/*
void updTimestampMap(keyName, dt=null) {
	def data = state?.timestampDtMap ?: [:]
	if(keyName) { data[keyName] = dt }
	state?.timestampDtMap = data
}

def getTimestampVal(val) {
	def tsData = state?.timestampDtMap
	if(val && tsData && tsData[val]) { return tsData[val] }
	return null
}
*/

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

boolean daysOk(days) {
	if(days) {
		def dayFmt = new SimpleDateFormat("EEEE")
		if(getTimeZone()) { dayFmt.setTimeZone(getTimeZone()) }
		return days.contains(dayFmt.format(new Date())) ? false : true
	} else { return true }
}

def time2Str(time) {
	if(time) {
		def t = timeToday(time, getTimeZone())
		def f = new java.text.SimpleDateFormat("h:mm a")
		f.setTimeZone(getTimeZone() ?: timeZone(time))
		f.format(t)
	}
}

def epochToTime(tm) {
	def tf = new SimpleDateFormat("h:mm a")
		tf?.setTimeZone(getTimeZone())
	return tf.format(tm)
}

String getDtNow() {
	def now = new Date()
	return formatDt(now)
}

boolean modesOk(modeEntry) {
	boolean res = true
	if(modeEntry) {
		modeEntry?.each { m ->
			if(m.toString() == location?.mode.toString()) { res = false }
		}
	}
	return res
}

boolean isInMode(modeList) {
	if(modeList) {
		//log.debug "mode (${location.mode}) in list: ${modeList} | result: (${location?.mode in modeList})"
		return location.mode.toString() in modeList
	}
	return false
}

def notifValEnum(allowCust = true) {
	def valsC = [
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes", 1800:"30 Minutes",
		3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 1000000:"Custom"
	]
	def vals = [
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
	]
	return allowCust ? valsC : vals
}
/*
def pollValEnum() {
	def vals = [
		60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes",
		600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 2700:"45 Minutes", 3600:"60 Minutes"
	]
	return vals
}

def waitValEnum() {
	def vals = [
		1:"1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds",
		8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds", 15:"15 Seconds", 30:"30 Seconds"
	]
	return vals
}
*/

def strCapitalize(str) {
	return str ? str?.toString().capitalize() : null
}

/*
def getInputEnumLabel(inputName, enumName) {
	def result = "Not Set"
	if(inputName && enumName) {
		enumName.each { item ->
			if(item?.key.toString() == inputName?.toString()) {
				result = item?.value
			}
		}
	}
	return result
}
*/

def toJson(Map m) {
	return new org.json.JSONObject(m).toString()
}

/*
def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}
*/

/************************************************************************************************
|									LOGGING AND Diagnostic										|
*************************************************************************************************/

String lastN(String input, n) {
	return n > input?.size() ? input : input[-n..-1]
}

void LogTrace(String msg, String logSrc=(String)null) {
	boolean trOn = (settings?.showDebug && settings?.advAppDebug) ? true : false
	if(trOn) {
		boolean logOn = (state?.enRemDiagLogging) ? true : false
		Logger(msg, "trace", logSrc, logOn)
	}
}

void LogAction(String msg, String type="debug", boolean showAlways=false, String logSrc=null) {
	boolean isDbg = settings?.showDebug ? true : false
	if(showAlways || (isDbg && !showAlways)) { Logger(msg, type, logSrc) }
}

void Logger(String msg, String type="debug", String logSrc=(String)null, boolean noSTlogger=false) {
	if(msg && type) {
		String labelstr = ""
		if(state?.dbgAppndName == null) {
			def tval = parent ? parent.getSettingVal("dbgAppndName") : settings?.dbgAppndName
			state?.dbgAppndName = (tval || tval == null) ? true : false
		}
		if(state?.dbgAppndName) { labelstr = "${app.label} | " }
		String themsg = "${labelstr}${msg}"
		//log.debug "Logger remDiagTest: $msg | $type | $logSrc"

		if(state?.enRemDiagLogging == null) {
			state?.enRemDiagLogging = parent?.getStateVal("enRemDiagLogging")
			if(state?.enRemDiagLogging == null) {
			       state?.enRemDiagLogging = false
			}
			if(!state?.enRemDiagLogging) {
				atomicState?.remDiagLogDataStore = []
				atomicState?.remDiagLogpDataStore = []
			}
			//log.debug "set enRemDiagLogging to ${state?.enRemDiagLogging}"
		}
		if(state?.enRemDiagLogging) {
			String theId = lastN(app?.id.toString(),5)
			String theLogSrc = (logSrc == null) ? (parent ? "Automation-${theId}" : "NestManager") : logSrc
			parent?.saveLogtoRemDiagStore(themsg, type, theLogSrc)
		} else {
		if(!noSTlogger) {
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

///////////////////////////////////////////////////////////////////////////////
/******************************************************************************
|				Application Help and License Info Variables					|
*******************************************************************************/
///////////////////////////////////////////////////////////////////////////////
String appName()		{ return "${appLabel()}" }
String appLabel()		{ return "NST Diagnostics" }
String gitRepo()		{ return "tonesto7/nest-manager"}
String gitBranch()		{ return "master" }
String gitPath()		{ return "${gitRepo()}/${gitBranch()}"}
//def betaMarker()	{ return false }
//def appDevType()	{ return false }
//def appDevName()	{ return "" }
/*
def appInfoDesc()	{
	def cur = null //parent ? parent?.state?.appData?.updater?.versions?.autoapp?.ver.toString() : null
	def beta = "" // betaMarker() ? " Beta" : ""
	def str = ""
	str += "${appName()}"
	//str += isAppUpdateAvail() ? "\n• ${textVersion()} (Latest: v${cur})${beta}" : "\n• ${textVersion()}${beta}"
	//str += "\n• ${textModified()}"
	return str
}
def textVersion()	{ return "Version: ${appVersion()}" }
*/
