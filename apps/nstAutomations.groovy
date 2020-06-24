/********************************************************************************************
|    Application Name: NST Automations                                                      |
|    Copyright (C) 2017, 2018, 2019 Anthony S.                                              |
|    Authors: Anthony S. (@tonesto7), Eric S. (@E_sch)                                      |
|    Contributors: Ben W. (@desertblade)                                                    |
|    A few code methods are modeled from those in CoRE by Adrian Caramaliu                  |
|                                                                                           |
|    June 18, 2020                                                                         |
|    License Info: https://github.com/tonesto7/nest-manager/blob/master/app_license.txt     |
|********************************************************************************************/

import groovy.json.*
import java.text.SimpleDateFormat
import groovy.transform.Field

definition(
	name: "NST Automations",
	namespace: "tonesto7",
	author: "Anthony S.",
	parent: "tonesto7:NST Manager",
	description: "This App is used to enable built-in automations for NST Manager",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/apps/nstAutomations.groovy")

static String appVersion(){ "2.0.6" }

preferences{
	page(name: "startPage")

	//Automation Pages
	page(name: "notAllowedPage")
	page(name: "selectAutoPage")
	page(name: "mainAutoPage")
	page(name: "mainAutoPage1")
	page(name: "mainAutoPage2")
	page(name: "remSenShowTempsPage")
	page(name: "nestModePresPage")
	page(name: "schMotModePage")
	page(name: "watchDogPage")

	//shared pages
	page(name: "schMotSchedulePage")
	page(name: "schMotSchedulePage1")
	page(name: "schMotSchedulePage2")
	page(name: "schMotSchedulePage3")
	page(name: "schMotSchedulePage4")
	page(name: "schMotSchedulePage5")
	page(name: "schMotSchedulePage6")
	page(name: "schMotSchedulePage7")
	page(name: "schMotSchedulePage8")

	page(name: "scheduleConfigPage")

	page(name: "tstatConfigAutoPage")
	page(name: "tstatConfigAutoPage1")
	page(name: "tstatConfigAutoPage2")
	page(name: "tstatConfigAutoPage3")
	page(name: "tstatConfigAutoPage4")
	page(name: "tstatConfigAutoPage5")
	page(name: "tstatConfigAutoPage6")
	page(name: "tstatConfigAutoPage7")

	page(name: "setNotificationPage")
	page(name: "setNotificationPage1")
	page(name: "setNotificationPage2")
	page(name: "setNotificationPage3")
	page(name: "setNotificationPage4")
	page(name: "setNotificationPage5")

	page(name: "setDayModeTimePage")
	page(name: "setDayModeTimePage1")
	page(name: "setDayModeTimePage2")
	page(name: "setDayModeTimePage3")
	page(name: "setDayModeTimePage4")
	page(name: "setDayModeTimePage5")
	//page(name: "setNotificationTimePage")
}

/******************************************************************************
|					Application Pages						|
*******************************************************************************/

def startPage(){
	//log.info "startPage"
	if(parent){
		Boolean t0=parent.getStateVal("ok2InstallAutoFlag")
		if( /* !state.isInstalled && */ t0 != true){
			//Logger("Not installed ${t0}")
			notAllowedPage()
		}else{
			state.isParent=false
			selectAutoPage()
		}
	}else{
		notAllowedPage()
	}
}

def notAllowedPage (){
	dynamicPage(name: "notAllowedPage", title: "This install Method is Not Allowed", install: false, uninstall: true){
		section(){
			paragraph imgTitle(getAppImg("disable_icon2.png"), paraTitleStr("WE HAVE A PROBLEM!\n\nNST Automations can't be directly installed.\n\nPlease use the Nest Integrations App to configure them.")), required: true, state: null
		}
	}
}

private Boolean isHubitat(){
	return hubUID != null
}

void installed(){
	log.debug "${app.getLabel()} Installed with settings: ${settings}"		// MUST BE log.debug
	if(isHubitat() && !app.id) return
	initialize()
}

void updated(){
	log.debug "${app.getLabel()} Updated...with settings: ${settings}"
	state.isInstalled=true
	String appLbl=getCurAppLbl()
	if(appLbl?.contains("Watchdog")){
		if(!state.autoTyp){ state.autoTyp="watchDog" }
	}
	initialize()
	state.lastUpdatedDt=getDtNow()
}

void uninstalled(){
	log.debug "uninstalled"
	uninstAutomationApp()
}

void initialize(){
	log.debug "${app.label} Initialize..."			// Must be log.debug
	if(!state.isInstalled){ state.isInstalled=true }
	//Boolean settingsReset=parent.getSettingVal("resetAllData")
	//if(state.resetAllData || settingsReset){
	//	if(fixState()){ return }	// runIn of fixState will call initAutoApp()
	//}
	runIn(6, "initAutoApp", [overwrite: true])
}

def subscriber(){
}

private adj_temp(tempF){
	if(getTemperatureScale() == "C"){
		return ((tempF - 32) * ( 5/9 )).toDouble()
	}else{
		return tempF
	}
}

void setMyLockId(val){
	if(state.myID == null && parent && val){
		state.myID=val.toString()
	}
}

String getMyLockId(){
	if(parent){ return state.myID }else{ return null }
}

/*
def fixState(){
	def result=false
	LogTrace("fixState")
	def before=getStateSizePerc()
	if(!state.resetAllData && parent.getSettingVal("resetAllData")){ // automation cleanup called from update() -> initAutoApp()
		def data=getState()?.findAll { !(it?.key in [ "autoTyp", "autoDisabled", "scheduleList", "resetAllData", "autoDisabledDt",
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
		result=true
	}else if(state.resetAllData && !parent.getSettingVal("resetAllData")){
		LogAction("fixState: resetting ALL toggle", "info", true)
		state.resetAllData=false
	}

	if(result){
		state.resetAllData=true
		LogAction("fixState: State Data: before: $before after: ${getStateSizePerc()}", "info", true)
		runIn(20, "finishFixState", [overwrite: true])
	}
	return result
}

void finishFixState(migrate=false){
	LogTrace("finishFixState")
	if(state.resetAllData || migrate){
		def tstat=settings.schMotTstat
		if(tstat){
			LogAction("finishFixState found tstat", "info", true)
			getTstatCapabilities(tstat, schMotPrefix())
			if(!getMyLockId()){
				setMyLockId(app.id)
			}
			if(settings.schMotRemoteSensor){
				LogAction("finishFixState found remote sensor", "info", true)
				if( parent?.remSenLock(tstat?.deviceNetworkId, getMyLockId()) ){  // lock new ID
					state.remSenTstat=tstat?.deviceNetworkId
				}
				if(isRemSenConfigured() && settings.remSensorDay){
					LogAction("finishFixState found remote sensor configured", "info", true)
					if(settings.vthermostat != null){ parent?.addRemoveVthermostat(tstat.deviceNetworkId, vthermostat, getMyLockId()) }
				}
			}
		}
		if(!migrate){ initAutoApp() }
		//updated()
	}
}
*/

def selectAutoPage(){
	//LogTrace("selectAutoPage()")
	if(!state.autoTyp){
		return dynamicPage(name: "selectAutoPage", title: "Choose an Automation Type", uninstall: false, install: true, nextPage: null){
			Boolean thereIsChoice=!parent.automationNestModeEnabled(null)
			if(thereIsChoice){
				section("Set Nest Presence Based on location Modes, Presence Sensor, or Switches:"){
					href "mainAutoPage1", title: imgTitle(getAppImg("mode_automation_icon.png"), inputTitleStr("Nest Mode Automations")), description: ""//, params: ["aTyp": "nMode"]
				}
			}
			section("Thermostat Automations: Setpoints, Remote Sensor, External Temp, Humidifier, Contact Sensor, Leak Sensor, Fan Control"){
				href "mainAutoPage2", title: imgTitle(getAppImg("thermostat_automation_icon.png"), inputTitleStr("Thermostat Automations")), description: "" //, params: ["aTyp": "schMot"]
			}
		}
	}
	else { return mainAutoPage( [aTyp: state.autoTyp]) }
}

private static String sectionTitleStr(String title)	{ return '<h3>'+title+'</h3>' }
private static String inputTitleStr(String title)	{ return '<u>'+title+'</u>' }
private static String pageTitleStr(String title)	{ return '<h1>'+title+'</h1>' }
private static String paraTitleStr(String title)	{ return '<b>'+title+'</b>' }

static String imgTitle(String imgSrc, String titleStr, String color=(String)null, Integer imgWidth=30, Integer imgHeight=0){
	String imgStyle=""
	imgStyle += imgWidth>0 ? 'width: '+imgWidth.toString()+'px !important;':''
	imgStyle += imgHeight>0 ? imgWidth!=0 ? ' ':''+'height: '+imgHeight.toString()+'px !important;':''
	if(color!=(String)null){ return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""" }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""" }
}

// string table for titles
static String titles(String name, Object... args){
	Map page_titles=[
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
	if(args)
		return String.format(page_titles[name], args)
	else
		return page_titles[name]
}

// string table for descriptions
static String descriptions(String name, Object... args){
	Map element_descriptions=[
		"d_ttc": "Tap to configure",
		"d_ttm": "\n\nTap to modify"
	]
	if(args)
		return String.format((String)element_descriptions[name],args)
	else
		return (String)element_descriptions[name]
}

static String icons(String name, String napp="App"){
	Map icon_names=[
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
	String t0=icon_names?."${name}"
	//LogAction("t0 ${t0}", "warn", true)
	if(t0) return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${t0}_icon.png".toString()
	else return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${name}".toString()
}

static String getAppImg(String imgName, Boolean on=true){
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/App/$imgName" : ""
	return on ? icons(imgName) : ""
}

static String getDevImg(String imgName, Boolean on=true){
	//return (!disAppIcons || on) ? "https://raw.githubusercontent.com/${gitPath()}/Images/Devices/$imgName" : ""
	return  on ? icons(imgName, "Devices") : ""
}

def mainAutoPage1(params){
	//LogTrace("mainAutoPage1()")
	Map t0=[:]
	t0.aTyp="nMode"
	return mainAutoPage( t0 ) //[autoType: "nMode"])
}

def mainAutoPage2(params){
	//LogTrace("mainAutoPage2()")
	Map t0=[:]
	t0.aTyp="schMot"
	return mainAutoPage( t0 ) //[autoType: "schMot"])
}

def mainAutoPage(Map params){
	//LogTrace("mainAutoPage()")
	String t0=getTemperatureScale()
	state.tempUnit=(t0 != (String)null) ? t0 : state.tempUnit
	if(!state.autoDisabled){ state.autoDisabled=false }
	String autoType=(String)null
	//If params.aTyp is not null then save to state.
	if(!state.autoTyp){
		if(!params?.aTyp){ Logger("nothing is set mainAutoPage") }
		else {
			//Logger("setting autoTyp")
			state.autoTyp=params?.aTyp
			autoType=params?.aTyp
		}
	}else{
		//Logger("setting autoTyp")
		autoType=state.autoTyp
	}

	//Logger("mainPage: ${state.autoTyp}  ${autoType}")
	// If the selected automation has not been configured take directly to the config page.  Else show main page
//Logger("in mainAutoPage ${autoType}  ${state.autoTyp}")
	if(autoType == "nMode" && !isNestModesConfigured())		{ return nestModePresPage() }
	else if(autoType == "watchDog" && !isWatchdogConfigured())	{ return watchDogPage() }
	else if(autoType == "schMot" && !isSchMotConfigured())		{ return schMotModePage() }

	else {
		//Logger("in main page")
		// Main Page Entries
		//return dynamicPage(name: "mainAutoPage", title: "Automation Configuration", uninstall: false, install: false, nextPage: "nameAutoPage" ){
		return dynamicPage(name: "mainAutoPage", title: pageTitleStr("Automation Configuration"), uninstall: true, install: true, nextPage:null ){
			section(){
				if(settings.autoDisabledreq){
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is currently disabled!\nTurn it back on to to make changes or resume operation")), required: true, state: null
				}else{
					if(getIsAutomationDisabled()){ paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is still disabled!\nPress Next and Done to Activate this Automation Again")), state: "complete" }
				}
				if(!getIsAutomationDisabled()){
					if(autoType == "nMode"){
						//paragraph paraTitleStr("Set Nest Presence Based on location Modes, Presence Sensor, or Switches:")
						String nDesc=""
						nDesc += isNestModesConfigured() ? "Nest Mode:\n • Status: (${strCapitalize(getNestLocPres())})" : ""
						if(((!nModePresSensor && !nModeSwitch) && (nModeAwayModes && nModeHomeModes))){
							nDesc += nModeHomeModes ? "\n • Home Modes: (${nModeHomeModes.size()})" : ""
							nDesc += nModeAwayModes ? "\n • Away Modes: (${nModeAwayModes.size()})" : ""
						}
						nDesc += (nModePresSensor && !nModeSwitch) ? "\n\n${nModePresenceDesc()}" : ""
						nDesc += (nModeSwitch && !nModePresSensor) ? "\n • Using Switch: (State: ${isSwitchOn(nModeSwitch) ? "ON" : "OFF"})" : ""
						nDesc += (settings.nModeDelay && settings.nModeDelayVal) ? "\n • Change Delay: (${getEnumValue(longTimeSecEnum(), settings.nModeDelayVal)})" : ""
						nDesc += (isNestModesConfigured() ) ? "\n • Restrictions Active: (${autoScheduleOk(getAutoType()) ? "NO" : "YES"})" : ""
						if(isNestModesConfigured()){
							nDesc += "\n • Set Thermostats to ECO: (${nModeSetEco ? "On" : "Off"})"
							if(parent.getSettingVal("cameras")){
								nDesc += "\n • Cams On when Away: (${nModeCamOnAway ? "On" : "Off"})"
								nDesc += "\n • Cams Off when Home: (${nModeCamOffHome ? "On" : "Off"})"
								if(settings.nModeCamsSel){
									nDesc += "\n • Nest Cams Selected: (${nModeCamsSel.size()})"
								}
							}
						}
						String t1=getNotifConfigDesc("nMode")
						nDesc += t1 ? "\n\n${t1}" : ""
						nDesc += t1 || (nModePresSensor || nModeSwitch) || (!nModePresSensor && !nModeSwitch && (nModeAwayModes && nModeHomeModes)) ? descriptions("d_ttm") : ""
						String nModeDesc=isNestModesConfigured() ? nDesc : null
						//Logger("nModeDesc ${nModeDesc}")
						href "nestModePresPage", title: imgTitle(getAppImg("mode_automation_icon.png"), inputTitleStr("Nest Mode Automation Config")), description: nModeDesc ?: descriptions("d_ttc"), state: (nModeDesc ? "complete" : null)
					}

					if(autoType == "schMot"){
//Logger("calling schMot config and page")
//Logger("in mainAutoPage7")
						String sModeDesc=(String)getSchMotConfigDesc()
						href "schMotModePage", title: imgTitle(getAppImg("thermostat_automation_icon.png"), inputTitleStr("Thermostat Automation Config")), description: sModeDesc ?: descriptions("d_ttc"), state: (sModeDesc ? "complete" : null)
					}

					if(autoType == "watchDog"){
						//paragraph paraTitleStr("Watch your Nest Location for Events:")
						String watDesc=""
						String t1=getNotifConfigDesc("watchDog")
						if(t1){
							def tstats=parent.getSettingVal("thermostats")
							def prots=parent.getSettingVal("protects")
							def cams=parent.getSettingVal("cameras")
							if(tstats || prots || cams){
								if(settings.onlineStatMon != false){
									t1 += "\n\nWatchDog Monitors:"
									t1 += "\n • Notify if device is offline"
									if(tstats){
										t1 += "\n • Notify on low temperature extremes"
										if(settings.thermMissedEco != false){
											t1 += "\n • Notify When Away and Thermostat not in Eco Mode"
										}
									}
									if(cams && (settings.onlineStatMon != false)){
										Boolean camStreamNotif=parent.getSettingVal("camStreamNotifMsg")
										if(camStreamNotif != false){
											t1 += "\n • Notify on Camera Streaming status changes"
										}
									}
								}
							}
							Boolean locPres=parent.getSettingVal("locPresChangeMsg")
							if(locPres != false){
								t1 += "\n • Notify Nest Home/Away Status changes"
							}
						}
						watDesc += t1 ? t1 + descriptions("d_ttm") : ""
						String watDogDesc=isWatchdogConfigured() ? watDesc : (String)null
						href "watchDogPage", title: imgTitle(getAppImg("watchdog_icon.png"), inputTitleStr(titles("t_nlw"))), description: watDogDesc ?: descriptions("d_ttc"), state: (watDogDesc ? "complete" : null)
					}
				}
			}
			section(sectionTitleStr("Automation Options:")){
				if(/* state.isInstalled && */ (isNestModesConfigured() || isWatchdogConfigured() || isSchMotConfigured())){
					//paragraph paraTitleStr("Enable/Disable this Automation")
					input "autoDisabledreq", "bool", title: imgTitle(getAppImg("disable_icon2.png"), inputTitleStr("Disable this Automation?")), required: false, defaultValue: false /* state.autoDisabled */, submitOnChange: true
					setAutomationStatus()
				}
				input ("showDebug", "bool", title: imgTitle(getAppImg("debug_icon.png"), inputTitleStr("Debug Option")), description: "Show ${app?.name} Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true)
				if(showDebug){
					input (name: "advAppDebug", type: "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose Logs?")), required: false, defaultValue: false, submitOnChange: true)
				}else{
					settingUpdate("advAppDebug", "false", "bool")
				}
			}
			section(paraTitleStr("Automation Name:")){
				String newName=getAutoTypeLabel()
				if(!app?.label){ app?.updateLabel(newName) }
				label title: imgTitle(getAppImg("name_tag_icon.png"), inputTitleStr("Label this Automation: Suggested Name: ${newName}")), defaultValue: "${newName}", required: true //, wordWrap: true
				if(!state.isInstalled){
					paragraph "Make sure to name it something that you can easily recognize."
				}
			}
		}
	}
}

def getSchMotConfigDesc(Boolean retAsList=false){
	if(!isSchMotConfigured()) return null
	List list=[]
	if(settings.schMotWaterOff){ list.push("Turn Off if Leak Detected") }
	if(settings.schMotContactOff){ list.push("Set ECO if Contact Open") }
	if(settings.schMotExternalTempOff){ list.push("Set ECO based on External Temp") }
	if(settings.schMotRemoteSensor){ list.push("Use Remote Temp Sensors") }
	if(isTstatSchedConfigured()){ list.push("Setpoint Schedules Created") }
	if(settings.schMotOperateFan){ list.push("Control Fans with HVAC") }
	if(settings.schMotHumidityControl){ list.push("Control Humidifier") }

	if(retAsList){
		return list
	}else{
		String sDesc=""
		sDesc += settings.schMotTstat ? "${settings.schMotTstat?.label}" : ""
		list?.each { String ls ->
			sDesc += "\n • "+ls
		}
		String t1=getNotifConfigDesc("schMot")
		sDesc += t1 ? "\n\n"+t1 : ""
		sDesc += settings.schMotTstat ? descriptions("d_ttm") : ""
		return sDesc
	}
}

void setAutomationStatus(Boolean upd=false){
	Boolean myDis=(settings.autoDisabledreq == true)
	Boolean settingsReset=(parent.getSettingVal("disableAllAutomations") == true)
	Boolean storAutoType=getAutoType() == "storage"
	if(settingsReset && !storAutoType){
		if(!myDis && settingsReset){ LogAction("setAutomationStatus: Nest Integrations forcing disable", "info", true) }
		myDis=true
	}else if(storAutoType){
		myDis=false
	}
	if(!getIsAutomationDisabled() && myDis){
		LogAction('Automation Disabled at ('+getDtNow()+')', "info", true)
		state.autoDisabledDt=getDtNow()
	}else if(getIsAutomationDisabled() && !myDis){
		LogAction('Automation Enabled at ('+getDtNow()+')', "info", true)
		state.autoDisabledDt=null
	}
	state.autoDisabled=myDis
	if(upd){ app.update() }
}

void settingUpdate(String name, value, String type=null){
	//LogTrace("settingUpdate($name, $value, $type)...")
	if(name){
		if(value == "" || value == null || value == []){
			settingRemove(name)
			return
		}
	}
	if(name && type){ app?.updateSetting(name, [type: type, value: value]) }
	else if(name && type == null){ app?.updateSetting(name, value) }
}

void settingRemove(String name){
	//LogTrace("settingRemove($name)...")
	if(name){ app?.clearSetting(name.toString()) }
}

def stateUpdate(String key, value){
	if(key){ state."${key}"=value; return true }
	//else { LogAction("stateUpdate: null key $key $value", "error", true); return false }
}

void stateRemove(String key){
	state.remove(key.toString())
}

void initAutoApp(){
	//log.debug "${app.label} initAutoApp..."			// Must be log.debug
	if(settings["watchDogFlag"]){
		state.autoTyp="watchDog"
	}

	String autoType=getAutoType()
	if(autoType == "nMode"){
		parent.automationNestModeEnabled(true)
	}
	unschedule()
	unsubscribe()
	//def autoDisabled=getIsAutomationDisabled()
	setAutomationStatus()

	automationsInst()

	if(autoType == "schMot" && isSchMotConfigured()){
		updateScheduleStateMap()
		List schedList=getScheduleList()
		Boolean timersActive=false
		def sLbl
		Integer cnt=1
		Integer numact=0
		schedList?.each { Integer scd ->
			sLbl="schMot_${scd}_"
			stateRemove("sched${cnt}restrictions")
			stateRemove("schedule${cnt}SwEnabled")
			stateRemove("schedule${cnt}PresEnabled")
			stateRemove("schedule${cnt}MotionEnabled")
			stateRemove("schedule${cnt}SensorEnabled")
			stateRemove("schedule${cnt}TimeActive")
			stateRemove("${sLbl}MotionActiveDt")
			stateRemove("${sLbl}MotionInActiveDt")
			stateRemove("${sLbl}oldMotionActive")
			stateRemove("motion${cnt}UseMotionSettings")
			stateRemove("motion${cnt}LastisBtwn")

			Map newscd=[:]
			Boolean act=settings["${sLbl}SchedActive"]
			if(act){
				newscd=cleanUpMap([
					m: settings["${sLbl}rstrctMode"],
					tf: settings["${sLbl}rstrctTimeFrom"],
					tfc: settings["${sLbl}rstrctTimeFromCustom"],
					tfo: settings["${sLbl}rstrctTimeFromOffset"],
					tt: settings["${sLbl}rstrctTimeTo"],
					ttc: settings["${sLbl}rstrctTimeToCustom"],
					tto: settings["${sLbl}rstrctTimeToOffset"],
					w: settings["${sLbl}restrictionDOW"],
					p1: buildDeviceNameList(settings["${sLbl}rstrctPHome"], "and"),
					p0: buildDeviceNameList(settings["${sLbl}rstrctPAway"], "and"),
					s1: buildDeviceNameList(settings["${sLbl}rstrctSWOn"], "and"),
					s0: buildDeviceNameList(settings["${sLbl}rstrctSWOff"], "and"),
					ctemp: roundTemp(settings["${sLbl}CoolTemp"]),
					htemp: roundTemp(settings["${sLbl}HeatTemp"]),
					hvacm: settings["${sLbl}HvacMode"],
					sen0: settings["schMotRemoteSensor"] ? buildDeviceNameList(settings["${sLbl}remSensor"], "and") : null,
					thres: settings["schMotRemoteSensor"] ? settings["${sLbl}remSenThreshold"] : null,
					m0: buildDeviceNameList(settings["${sLbl}Motion"], "and"),
					mctemp: settings["${sLbl}Motion"] ? roundTemp(settings["${sLbl}MCoolTemp"]) : null,
					mhtemp: settings["${sLbl}Motion"] ? roundTemp(settings["${sLbl}MHeatTemp"]) : null,
					mhvacm: settings["${sLbl}Motion"] ? settings["${sLbl}MHvacMode"] : null,
//					mpresHome: settings["${sLbl}Motion"] ? settings["${sLbl}MPresHome"] : null,
//					mpresAway: settings["${sLbl}Motion"] ? settings["${sLbl}MPresAway"] : null,
					mdelayOn: settings["${sLbl}Motion"] ? settings["${sLbl}MDelayValOn"] : null,
					mdelayOff: settings["${sLbl}Motion"] ? settings["${sLbl}MDelayValOff"] : null
				])
				numact += 1

				//LogTrace("initAutoApp: [Schedule: $scd | sLbl: $sLbl | act: $act | newscd: $newscd]")
				state."sched${cnt}restrictions"=newscd
				state."schedule${cnt}SwEnabled"=(newscd?.s1 || newscd?.s0)
				state."schedule${cnt}PresEnabled"=(newscd?.p1 || newscd?.p0)
				state."schedule${cnt}MotionEnabled"=!!(newscd?.m0)
				state."schedule${cnt}SensorEnabled"=!!(newscd?.sen0)
				//state."schedule${cnt}FanCtrlEnabled"=!!(newscd?.fan0)
				state."schedule${cnt}TimeActive"=(newscd?.tf || newscd?.tfc || newscd?.tfo || newscd?.tt || newscd?.ttc || newscd?.tto || newscd?.w)

				Boolean newact=isMotionActive(settings["${sLbl}Motion"])
				if(newact){ state."${sLbl}MotionActiveDt"=getDtNow() }
				else { state."${sLbl}MotionInActiveDt"=getDtNow() }

				state."${sLbl}oldMotionActive"=newact
				state."motion${cnt}UseMotionSettings"=null		// clear automation state of schedule in use motion state
				state."motion${cnt}LastisBtwn"=false
			}

			timersActive=(timersActive || state."schedule${cnt}TimeActive")

			cnt += 1
		}
		state.scheduleTimersActive=timersActive
		state.schedLast=null	// clear automation state of schedule in use
		state.scheduleActiveCnt=numact
	}

	subscribeToEvents()
	scheduler()

	app.updateLabel(getAutoTypeLabel())
	LogAction("Automation Label: ${getAutoTypeLabel()}", "info", false)

	stateRemove("motionnullLastisBtwn")
	//state.remove("motion1InBtwn")
	//state.remove("motion2InBtwn")
	//state.remove("motion3InBtwn")
	//state.remove("motion4InBtwn")
	//state.remove("TstatTurnedOff")
	//state.remove("schedule{1}TimeActive")
	//state.remove("schedule{2}TimeActive")
	//state.remove("schedule{3}TimeActive")
	//state.remove("schedule{4}TimeActive")
	//state.remove("schedule{5}TimeActive")
	//state.remove("schedule{6}TimeActive")
	//state.remove("schedule{7}TimeActive")
	//state.remove("schedule{8}TimeActive")
	//state.remove("lastaway")

	stateRemove("evalSched")
	stateRemove("dbgAppndName")   // cause Automations to re-check with parent for value
	stateRemove("wDevInst")   // cause Automations to re-check with parent for value after updated is called
	stateRemove("enRemDiagLogging") // cause recheck

	Boolean dbgState=settings.showDebug || settings.advAppDebug
	if(!dbgState){ settingUpdate("showDebug", "true",  "bool"); dbgState=true }
	//settingUpdate("advAppDebug", "false", "bool")

	stateRemove("detailEventHistory")
	stateRemove("detailExecutionHistory")

	scheduleAutomationEval(30)
	if(dbgState){ runIn(1800, logsOff) }
}

void logsOff(){
	log.warn "debug logging disabled..."
	settingUpdate("showDebug", "false",  "bool")
	settingUpdate("advAppDebug", "false", "bool")
}

def uninstAutomationApp(){
	//LogTrace("uninstAutomationApp")
	String autoType=getAutoType()
	if(autoType == "schMot"){
		removeVstat("uninstAutomationApp")
	}
	if(autoType == "nMode"){
		parent.automationNestModeEnabled(false)
	}
}

String getCurAppLbl(){ return app?.label?.toString() }

String getAutoTypeLabel(){
	//LogTrace("getAutoTypeLabel()")
	String type=state.autoTyp
	String appLbl=getCurAppLbl()
	String newName=appName() == appLabel() ? "NST Automations" : appName()
	String typeLabel=""
	String newLbl
	String dis=(getIsAutomationDisabled() == true) ? "\n(Disabled)" : ""

	if(type == "nMode")		{ typeLabel="${newName} (NestMode)" }
	else if(type == "watchDog")	{ typeLabel="Nest Location ${location.name} Watchdog"}
	else if(type == "schMot")	{ typeLabel="${newName} (${settings.schMotTstat?.label})" }

//log.info "getAutoTypeLabel: ${type} ${appLbl}  ${appName()} ${appLabel()} ${typeLabel}"

	if(appLbl && appLbl != "Nest Manager" && appLbl != appLabel()){
		if(appLbl.contains("\n(Disabled)")){
			newLbl=appLbl.replaceAll('\\\n\\(Disabled\\)', '')
		}else{
			newLbl=appLbl
		}
	}else{
		newLbl=typeLabel
	}
	return newLbl+dis
}

/*
def getAppStateData(){
	return getState()
}
*/

def getSettingsData(){
	def sets=[]
	settings.sort().each { st ->
		sets << st
	}
	return sets
}

def getSettingVal(var){
	if(var == null){ return settings }
	return settings[var] ?: null
}

def getStateVal(var){
	return state[var] ?: null
}

public void automationsInst(){
	state.isNestModesConfigured =	(isNestModesConfigured() == true)
	state.isWatchdogConfigured =	(isWatchdogConfigured() == true)
	state.isSchMotConfigured =		(isSchMotConfigured() == true)
	state.isLeakWatConfigured =		(isLeakWatConfigured() == true)
	state.isConWatConfigured =		(isConWatConfigured() == true)
	state.isHumCtrlConfigured =		(isHumCtrlConfigured() == true)
	state.isExtTmpConfigured =		(isExtTmpConfigured() == true)
	state.isRemSenConfigured =		(isRemSenConfigured() == true)
	state.isTstatSchedConfigured =	(isTstatSchedConfigured() == true)
	state.isFanCtrlConfigured =		(isFanCtrlSwConfigured() == true)
	state.isFanCircConfigured =		(isFanCircConfigured() == true)
	state.isInstalled=true
}

List getAutomationsInstalled(){
	List list=[]
	String aType=state.autoTyp
	switch(aType){
		case "nMode":
			list.push(aType)
			break
		case "schMot":
			Map tmp=[:]
			tmp[aType]=[]
			if(isLeakWatConfigured())		{ tmp[aType].push("leakWat") }
			if(isConWatConfigured())		{ tmp[aType].push("conWat") }
			if(isHumCtrlConfigured())		{ tmp[aType].push("humCtrl") }
			if(isExtTmpConfigured())		{ tmp[aType].push("extTmp") }
			if(isRemSenConfigured())		{ tmp[aType].push("remSen") }
			if(isTstatSchedConfigured())		{ tmp[aType].push("tSched") }
			if(isFanCtrlSwConfigured())		{ tmp[aType].push("fanCtrl") }
			if(isFanCircConfigured())		{ tmp[aType].push("fanCirc") }
			if(tmp?.size()){ list.push(tmp) }
			break
		case "watchDog":
			list.push(aType)
			break
	}
	//LogTrace("getAutomationsInstalled List: $list")
	return list
}

String getAutomationType(){
	return state.autoTyp ?: (String)null
}

String getAutoType(){ return !parent ? "" : (String)state.autoTyp }

Boolean getIsAutomationDisabled(){
	Boolean dis=state.autoDisabled
	return (dis != null && dis == true)
}

void subscribeToEvents(){
	//Remote Sensor Subscriptions
	String autoType=getAutoType()
	List swlist=[]

	//Nest Mode Subscriptions
	if(autoType == "nMode"){
		if(isNestModesConfigured()){
			if(!settings.nModePresSensor && !settings.nModeSwitch && (settings.nModeHomeModes || settings.nModeAwayModes)){ subscribe(location, "mode", nModeGenericEvt) }
			if(settings.nModePresSensor && !settings.nModeSwitch){ subscribe(nModePresSensor, "presence", nModeGenericEvt) }
			if(settings.nModeSwitch && !settings.nModePresSensor){ subscribe(nModeSwitch, "switch", nModeGenericEvt) }

/*
			def tstats=parent.getSettingVal("thermostats")
			def foundTstats
			if(tstats){
				foundTstats=tstats?.collect { dni ->
					def d1=parent.getDevice(dni)
					if(d1){
						//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)

						//subscribe(d1, "ThermostatMode", automationGenericEvt) // this is not needed for nMode
						//subscribe(d1, "presence", automationGenericEvt) // this is not needed, tracking only
					}
					return d1
				}
			}
*/
			List t0=[]
			if(settings["nModerstrctSWOn"]){ t0=t0 + settings["nModerstrctSWOn"] }
			if(settings["nModerstrctSWOff"]){ t0=t0 + settings["nModerstrctSWOff"] }
			for(sw in t0){
				if(swlist?.contains(sw)){
					//log.trace "found $sw"
				}else{
					swlist.push(sw)
					subscribe(sw, "switch", automationGenericEvt)
				}
			}
		}
	}

	//ST Thermostat Motion
	if(autoType == "schMot"){
		if(isSchMotConfigured()){
			if(settings.schMotWaterOff){
				if(isLeakWatConfigured()){
					//setInitialVoiceMsgs(leakWatPrefix())
					//setCustomVoice(leakWatPrefix())
					subscribe(leakWatSensors, "water", leakWatSensorEvt)
				}
			}
			if(settings.schMotContactOff){
				if(isConWatConfigured()){
					//setInitialVoiceMsgs(conWatPrefix())
					//setCustomVoice(conWatPrefix())
					subscribe(conWatContacts, "contact", conWatContactEvt)
					List t0=[]
					if(settings["conWatrstrctSWOn"]){ t0=t0 + settings["conWatrstrctSWOn"] }
					if(settings["conWatrstrctSWOff"]){ t0=t0 + settings["conWatrstrctSWOff"] }
					for(sw in t0){
						if(swlist?.contains(sw)){
							//log.trace "found $sw"
						}else{
							swlist.push(sw)
							subscribe(sw, "switch", automationGenericEvt)
						}
					}
				}
			}
			if(settings.schMotHumidityControl){
				if(isHumCtrlConfigured()){
					subscribe(humCtrlSwitches, "switch", automationGenericEvt)
					subscribe(humCtrlHumidity, "humidity", automationGenericEvt)
					if(!settings.humCtrlUseWeather && settings.humCtrlTempSensor){ subscribe(humCtrlTempSensor, "temperature", automationGenericEvt) }
					if(settings.humCtrlUseWeather){
						//state.needWeathUpd=true
						def weather=parent.getSettingVal("weatherDevice")
						if(weather){
							subscribe(weather, "temperature", extTmpGenericEvt)
						}else{ LogAction("No weather device found", "error", true) }
					}
					def t0=[]
					if(settings["humCtrlrstrctSWOn"]){ t0=t0 + settings["humCtrlrstrctSWOn"] }
					if(settings["humCtrlrstrctSWOff"]){ t0=t0 + settings["humCtrlrstrctSWOff"] }
					for(sw in t0){
						if(swlist?.contains(sw)){
							//log.trace "found $sw"
						}else{
							swlist.push(sw)
							subscribe(sw, "switch", automationGenericEvt)
						}
					}
				}
			}

			if(settings.schMotExternalTempOff){
				if(isExtTmpConfigured()){
					//setInitialVoiceMsgs(extTmpPrefix())
					//setCustomVoice(extTmpPrefix())
					if(settings.extTmpUseWeather){
						//state.needWeathUpd=true
						def weather=parent.getSettingVal("weatherDevice")
						if(weather){
							subscribe(weather, "temperature", extTmpGenericEvt)
							subscribe(weather, "humidity", extTmpGenericEvt)
						}else{ LogAction("No weather device found", "error", true) }
					}
					def t0=[]
					if(settings["extTmprstrctSWOn"]){ t0=t0 + settings["extTmprstrctSWOn"] }
					if(settings["extTmprstrctSWOff"]){ t0=t0 + settings["extTmprstrctSWOff"] }
					for(sw in t0){
						if(swlist?.contains(sw)){
							//log.trace "found $sw"
						}else{
							swlist.push(sw)
							subscribe(sw, "switch", automationGenericEvt)
						}
					}
					if(!settings.extTmpUseWeather && settings.extTmpTempSensor){ subscribe(extTmpTempSensor, "temperature", extTmpGenericEvt) }
					state.extTmpChgWhileOnDt=getDtNow()
					state.extTmpChgWhileOffDt=getDtNow()
				}
			}
			List senlist=[]
			if(settings.schMotRemoteSensor){
				if(isRemSenConfigured()){
					if(settings.remSensorDay){
						for(sen in settings.remSensorDay){
							if(senlist?.contains(sen)){
								//log.trace "found $sen"
							}else{
								senlist.push(sen)
								subscribe(sen, "temperature", automationGenericEvt)
								subscribe(sen, "humidity", automationGenericEvt)
								if(settings.schMotExternalTempOff){
									if(isExtTmpConfigured()){
										subscribe(sen, "temperature", extTmpGenericEvt)
										subscribe(sen, "humidity", extTmpGenericEvt)
									}
								}
							}
						}
					}
				}
			}
			if(isTstatSchedConfigured()){ }
			if(settings.schMotOperateFan){
				if(isFanCtrlSwConfigured() && fanCtrlFanSwitches){
					subscribe(fanCtrlFanSwitches, "switch", automationGenericEvt)
					subscribe(fanCtrlFanSwitches, "level", automationGenericEvt)
				}
				List t0=[]
				if(settings["fanCtrlrstrctSWOn"]){ t0=t0 + settings["fanCtrlrstrctSWOn"] }
				if(settings["fanCtrlrstrctSWOff"]){ t0=t0 + settings["fanCtrlrstrctSWOff"] }
				for(sw in t0){
					if(swlist?.contains(sw)){
						//log.trace "found $sw"
					}else{
						swlist.push(sw)
						subscribe(sw, "switch", automationGenericEvt)
					}
				}
			}
			Boolean hasFan=(state.schMotTstatHasFan == true)
			if(hasFan && (settings.schMotOperateFan || settings.schMotRemoteSensor || settings.schMotHumidityControl)){
				subscribe(settings.schMotTstat, "thermostatFanMode", automationGenericEvt)
			}

			List schedList=getScheduleList()
			Integer cnt=1
			List prlist=[]
			List mtlist=[]
			schedList?.each { Integer scd ->
				String sLbl="schMot_${scd}_"
				def restrict=state."sched${cnt}restrictions"
				Boolean act=settings["${sLbl}SchedActive"]
				if(act){
					if(state."schedule${cnt}SwEnabled"){
						if(restrict?.s1){
							for(sw in settings["${sLbl}rstrctSWOn"]){
								if(swlist?.contains(sw)){
									//log.trace "found $sw"
								}else{
									swlist.push(sw)
									subscribe(sw, "switch", automationGenericEvt)
								}
							}
						}
						if(restrict?.s0){
							for(sw in settings["${sLbl}rstrctSWOff"]){
								if(swlist?.contains(sw)){
									//log.trace "found $sw"
								}else{
									swlist.push(sw)
									subscribe(sw, "switch", automationGenericEvt)
								}
							}
						}
					}
					if(state."schedule${cnt}PresEnabled"){
						if(restrict?.p1){
							for(pr in settings["${sLbl}rstrctPHome"]){
								if(prlist?.contains(pr)){
									//log.trace "found $pr"
								}else{
									prlist.push(pr)
									subscribe(pr, "presence", automationGenericEvt)
								}
							}
						}
						if(restrict?.p0){
							for(pr in settings["${sLbl}rstrctPAway"]){
								if(prlist?.contains(pr)){
									//log.trace "found $pr"
								}else{
									prlist.push(pr)
									subscribe(pr, "presence", automationGenericEvt)
								}
							}
						}
					}
					if(state."schedule${cnt}MotionEnabled"){
						if(restrict?.m0){
							for(mt in settings["${sLbl}Motion"]){
								if(mtlist?.contains(mt)){
									//log.trace "found $mt"
								}else{
									mtlist.push(mt)
									subscribe(mt, "motion", automationMotionEvt)
								}
							}
						}
					}
					if(state."schedule${cnt}SensorEnabled"){
						if(restrict?.sen0){
							for(sen in settings["${sLbl}remSensor"]){
								if(senlist?.contains(sen)){
									//log.trace "found $sen"
								}else{
									senlist.push(sen)
									subscribe(sen, "temperature", automationGenericEvt)
								}
							}
						}
					}
				}
				cnt += 1
			}
			subscribe(settings.schMotTstat, "thermostatMode", automationGenericEvt)
			subscribe(settings.schMotTstat, "thermostatOperatingState", automationGenericEvt)
			subscribe(settings.schMotTstat, "temperature", automationGenericEvt)
			subscribe(settings.schMotTstat, "presence", automationGenericEvt)
			Boolean canCool=state.schMotTstatCanCool
			if(canCool){
				subscribe(settings.schMotTstat, "coolingSetpoint", automationGenericEvt)
			}
			Boolean canHeat=state.schMotTstatCanHeat
			if(canHeat){
				subscribe(settings.schMotTstat, "heatingSetpoint", automationGenericEvt)
			}
			subscribe(location, "sunset", automationGenericEvt)
			subscribe(location, "sunrise", automationGenericEvt)
			subscribe(location, "mode", automationGenericEvt)
		}
	}
	//watchDog Subscriptions
	if(autoType == "watchDog"){
		// if(isWatchdogConfigured())
		def tstats=parent.getSettingVal("thermostats")
		def foundTstats
		if(tstats){
			foundTstats=tstats?.collect { dni ->
				def d1=parent.getDevice(dni)
				if(d1){
					//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)

					subscribe(d1, "temperature", automationGenericEvt)
					subscribe(d1, "thermostatMode", automationGenericEvt)
					subscribe(d1, "presence", automationGenericEvt)
					subscribe(d1, "onlineStatus", automationGenericEvt)
					subscribe(location, "mode", automationGenericEvt)
				}
				return d1
			}
		}
		def prots=parent.getSettingVal("protects")
		def foundProts
		if(prots){
			foundProts=prots?.collect { dni ->
				def d1=parent.getDevice(dni)
				if(d1){
					//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)

					subscribe(d1, "onlineStatus", automationGenericEvt)
				}
				return d1
			}
		}

		def cams=parent.getSettingVal("cameras")
		def foundCams
		if(cams){
			foundCams=cams?.collect { dni ->
				def d1=parent.getDevice(dni)
				if(d1){
					//LogAction("Found: ${d1?.displayName} with (Id: ${dni?.key})", "debug", false)

					subscribe(d1, "onlineStatus", automationGenericEvt)
					subscribe(d1, "isStreaming", automationGenericEvt)
				}
				return d1
			}
		}

	}

	//Alarm status monitoring if any automation has alarm notification enabled
	if(settings["${autoType}AlarmDevices"] && settings."${pName}AllowAlarmNotif"){
		if(settings["${autoType}_Alert_1_Use_Alarm"] || settings["${autoType}_Alert_2_Use_Alarm"]){
			subscribe(settings["${autoType}AlarmDevices"], "alarm", alarmAlertEvt)
		}
	}
}

void scheduler(){
	def random=new Random()
	Integer random_int=random.nextInt(60)
	Integer random_dint=random.nextInt(9)

	String autoType=getAutoType()
	if(autoType == "schMot" && state.scheduleActiveCnt && state.scheduleTimersActive){
		LogTrace("${autoType} scheduled (${random_int} ${random_dint}/5 * * * ?)")
		schedule("${random_int} ${random_dint}/5 * * * ?", heartbeatAutomation)
	}else if(autoType != "remDiag" && autoType != "storage"){
		LogTrace("${autoType} scheduled (${random_int} ${random_dint}/30 * * * ?)")
		schedule("${random_int} ${random_dint}/30 * * * ?", heartbeatAutomation)
	}
}

void heartbeatAutomation(){
	String autoType=getAutoType()
	String str="heartbeatAutomation() ${autoType}"
	Integer val=900
	if(autoType == "schMot"){
		val=220
	}
	if(getAutoRunInSec() > val){
		LogTrace(str+' RUN')
		runAutomationEval()
	}else{
		LogTrace(str+' NOT NEEDED')
	}
}

Integer defaultAutomationTime(){
	return 20
}

void scheduleAutomationEval(Integer schedtime=defaultAutomationTime()){
	Integer theTime=schedtime
	if(theTime < defaultAutomationTime()){ theTime=defaultAutomationTime() }
	String autoType=getAutoType()
	def random=new Random()
	Integer random_int=random.nextInt(6)  // this randomizes a bunch of automations firing at same time off same event
	Boolean waitOverride=false
	switch(autoType){
		case "nMode":
			if(theTime == defaultAutomationTime()){
				theTime=14 + random_int  // this has nMode fire first as it may change the Nest Mode
			}
			break
		case "schMot":
			if(theTime == defaultAutomationTime()){
				theTime += random_int
			}
			Integer schWaitVal=settings.schMotWaitVal?.toInteger() ?: 60
			if(schWaitVal > 120){ schWaitVal=120 }
			Integer t0=getAutoRunSec()
			if((schWaitVal - t0) >= theTime ){
				theTime=(schWaitVal - t0)
				waitOverride=true
			}
			//theTime=Math.min( Math.max(theTime,defaultAutomationTime()), 120)
			break
		case "watchDog":
			if(theTime == defaultAutomationTime()){
				theTime=35 + random_int  // this has watchdog fire last so other automations can finish changes
			}
			break
	}
	if(!state.evalSched){
		runIn(theTime, "runAutomationEval", [overwrite: true])
		state.autoRunInSchedDt=getDtNow()
		state.evalSched=true
		state.evalSchedLastTime=theTime
	}else{
		String str="scheduleAutomationEval: "
		def t0=state.evalSchedLastTime
		if(t0 == null){ t0=0 }
		Integer timeLeftPrev=t0 - getAutoRunInSec()
		if(timeLeftPrev < 0){ timeLeftPrev=100 }
		String str1=" Schedule change: from (${timeLeftPrev}sec) to (${theTime}sec)"
		if(timeLeftPrev > (theTime + 5) || waitOverride){
			if(Math.abs(timeLeftPrev - theTime) > 3){
				runIn(theTime, "runAutomationEval", [overwrite: true])
				LogTrace(str+'Performing'+str1)
				state.autoRunInSchedDt=getDtNow()
				state.evalSched=true
				state.evalSchedLastTime=theTime
			}
		}else{ LogTrace(str+'Skipping'+str1) }
	}
}

//def getAutoRunInSec(){ return !state.autoRunInSchedDt ? 100000 : GetTimeDiffSeconds(state.autoRunInSchedDt, null, "getAutoRunInSec").toInteger() }
Integer getAutoRunInSec(){ return getTimeSeconds("autoRunInSchedDt", 100000, "getAutoRunInSec") }

void runAutomationEval(){
	LogTrace("runAutomationEval")
	String autoType=getAutoType()
	state.evalSched=false
	switch(autoType){
		case "nMode":
			if(isNestModesConfigured()){
				checkNestMode()
			}
			break
		case "schMot":
/* not needed if streaming
			if(state.needChildUpdate){
				state.needChildUpdate=false
				parent.setNeedChildUpdate()
			}
*/
			if(isSchMotConfigured()){
				schMotCheck()
			}
			break
		case "watchDog":
			if(isWatchdogConfigured()){
				watchDogCheck()
			}
			break
		default:
			LogAction("runAutomationEval: Invalid Option Received ${autoType}", "warn", true)
			break
	}
}

/*
void sendAutoChgToDevice(dev, autoType, chgDesc){
	if(dev && autoType && chgDesc){
		try {
			dev?.whoMadeChanges(autoType?.toString(), chgDesc?.toString(), getDtNow().toString())
		} catch (ex){
			log.error "sendAutoChgToDevice Exception:", ex
		}
	}
}
*/

/*
def sendEcoActionDescToDevice(dev, desc){
	if(dev && desc){
		try {
			dev?.ecoDesc(desc)		// THIS ONLY WORKS ON NEST THERMOSTATS
		} catch (ex){
			log.error "sendEcoActionDescToDevice Exception:", ex
		}
	}
}
*/

/*
def getAutomationStats(){
	return [
		"lastUpdatedDt":state.lastUpdatedDt,
		"lastEvalDt":state.autoRunDt,
		"lastEvent":state.lastEventData,
		"lastActionData":getAutoActionData(),
		"lastSchedDt":state.autoRunInSchedDt,
		"lastExecVal":state.autoExecMS,
		"execAvgVal":(state.evalExecutionHistory != [] ? getAverageValue(state.evalExecutionHistory) : null)
	]
}
*/

void storeLastAction(String actionDesc, String actionDt, String autoType, dev=null){
	if(actionDesc && actionDt){

		Map newVal=["actionDesc":actionDesc, "dt":actionDt, "autoType":autoType]
		state.lastAutoActionData=newVal

		List list=state.detailActionHistory ?: []
		Integer listSize=30
		if(list?.size() < listSize){
			list.push(newVal)
		}
		else if(list?.size() > listSize){
			Integer nSz=(list?.size()-listSize) + 1
			List nList=list?.drop(nSz)
			nList?.push(newVal)
			list=nList
		}
		else if(list?.size() == listSize){
			List nList=list?.drop(1)
			nList?.push(newVal)
			list=nList
		}
		if(list){ state.detailActionHistory=list }

/*
		if(dev){
			sendAutoChgToDevice(dev, autoType, actionDesc)		// THIS ONLY WORKS ON NEST THERMOSTATS
		}
*/
	}
}

/*
def getAutoActionData(){
	if(state.lastAutoActionData){
		return state.lastAutoActionData
	}
}
*/

def automationGenericEvt(evt){
	def startTime=now()
	def eventDelay=startTime - evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "info", false)

/* if streaming, this is not needed
	if(isRemSenConfigured() && settings.vthermostat){
		state.needChildUpdate=true
	}
	if(settings.humCtrlUseWeather && isHumCtrlConfigured()){
		state.needWeathUpd=true
	}
*/
	doTheEvent(evt)
}

def doTheEvent(evt){
	if(getIsAutomationDisabled()){ return }
	else {
		scheduleAutomationEval()
		storeLastEventData(evt)
	}
}

/******************************************************************************
|						WATCHDOG AUTOMATION LOGIC CODE						|
*******************************************************************************/
static String watchDogPrefix(){ return "watchDog" }

def watchDogPage(){
	String pName=watchDogPrefix()
	dynamicPage(name: "watchDogPage", title: pageTitleStr(titles("t_nlw")), uninstall: false, install: true){
		section(sectionTitleStr(titles("t_nt"))){
			String t0=getNotifConfigDesc(pName)
			String pageDesc=t0 ? "${t0}" + descriptions("d_ttm") : ""
			href "setNotificationPage1", title: imgTitle(getAppImg("i_not"), inputTitleStr(titles("t_nt"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
//ERS
			if(settings."${pName}NotifOn"){
				def tstats=parent.getSettingVal("thermostats")
				def prots=parent.getSettingVal("protects")
				def cams=parent.getSettingVal("cameras")
				if(tstats || prots || cams){
					input "onlineStatMon", "bool", title: paraTitleStr("Notify When Devices are offline?"), required: false, defaultValue: true, submitOnChange: true
				}
				if(tstats && (settings.onlineStatMon != false)){
					paragraph imgTitle(getAppImg("i_sw"), paraTitleStr("Temperature warnings on"))
					input "thermMissedEco", "bool", title: paraTitleStr("Notify When Away and Thermostat Not in Eco Mode?"), required: false, defaultValue: true, submitOnChange: true
				}
				if(cams && (settings.onlineStatMon != false)){
					def camStreamNotif=parent.getSettingVal("camStreamNotifMsg")
					Boolean mys=camStreamNotif == false ? false : true
					def iiact=mys ? "i_sw" : "switch_off_icon.png"
					//settingUpdate("camStNot", mys.toString(), "bool")
					paragraph imgTitle(getAppImg(iiact), paraTitleStr("Stream Notification (setting from mgr) ${mys}"))
					//input "camStNot", "bool", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Stream Notification (setting from mgr)")), required: false, defaultValue: mys, submitOnChange: true
				}
				def locPres=parent.getSettingVal("locPresChangeMsg")
				Boolean myp=locPres == false ? false : true
				String iact=myp ? "i_sw" : "switch_off_icon.png"
				paragraph imgTitle(getAppImg(iact), paraTitleStr("Nest Location Home/Away changes (setting from mgr) ${myp}"))
			}else{
				settingRemove("thermMissedEco")
				settingRemove("onlineStatMon")
			}
		}
	}
}

/*
def automationSafetyTempEvt(evt){
	def startTime=now()
	def eventDelay=startTime - evt.date.getTime()
	LogTrace("Event | Thermostat Safety Temp Exceeded: '${evt.displayName}' (${evt.value}) with a delay of ${eventDelay}ms")
	if(getIsAutomationDisabled()){ return }
	else {
		if(evt?.value == "true"){
			scheduleAutomationEval()
		}
	}
	storeLastEventData(evt)
}
*/

// Alarms will repeat every watDogRepeatMsgDelay (1 hr default) ALL thermostats
void watchDogCheck(){
	if(getIsAutomationDisabled()){ return }
	else {
		Long execTime=now()
		state.autoRunDt=getDtNow()
		def tstats=parent.getSettingVal("thermostats")
		def foundTstats
		if(tstats){
			foundTstats=tstats?.collect { dni ->
				if(checkOnline(dni)){
					def d1=parent.getDevice(dni)
					if(d1){
						if(!getSafetyTempsOk(d1)){
							watchDogAlarmActions(d1.displayName, dni, "temp")
							//LogAction("watchDogCheck: | Thermostat: ${d1?.displayName} Safety Temp Exceeded: ${exceeded}", "warn", true)
						}

						// This is allowing for warning if Nest has problem of system coming out of ECO while away
						Boolean nestModeAway=(d1?.currentPresence?.toString() == "not present")
						//def nestModeAway=(getNestLocPres() == "home") ? false : true
						if(nestModeAway){
							String curMode=d1?.currentThermostatMode?.toString()
							if(!(curMode in ["eco", "off" ])){
								watchDogAlarmActions(d1.displayName, dni, "eco")
								def pres=d1?.currentPresence?.toString()
								//LogAction("watchDogCheck: | Thermostat: ${d1?.displayName} is Away and Mode Is Not in ECO | CurMode: (${curMode}) | CurrentPresence: (${pres})", "warn", true)
							}
						}
					}
					return d1
				}
			}
		}

		def prots=parent.getSettingVal("protects")
		def foundProts
		if(prots){
			foundProts=prots?.collect { dni ->
				Boolean a=checkOnline(dni)
				return dni
			}
		}

		def cams=parent.getSettingVal("cameras")
		def foundCams
		if(cams){
			foundCams=cams?.collect { dni ->
				if(checkOnline(dni)){
					def d1=parent.getDevice(dni)
					if(d1){
						String lastStr=state."lastStr${dni}"
						String curStream=d1?.currentIsStreaming?.toString()
						lastStr=lastStr ?: curStream
						if(curStream){
							if(curStream != lastStr){
								watchDogAlarmActions(d1.displayName, dni, "stream", curStream, lastStr)
								//LogAction("watchDogCheck: | ${d1?.displayName} streaming changed | CurStream: (${curStream}) | prev: (${lastStr})", "warn", true)
							}
							state."lastStr${dni}"=curStream
						}
						return dni
					}
				}
			}
		}

		def locPres=parent.getSettingVal("locPresChangeMsg")
		Boolean myp=locPres == false ? false : true
		String curPres=parent.getLocationPresence() ?: ""
		String lastPres=state.lastPresence ?: ""
		if(lastpres && (lastPres != curPres)){
			state.lastPresence=curPres
			watchDogAlarmActions(location.name, "Location", "locPres", curPres, lastPres)
			//LogAction("watchDogCheck: | Nest Location changed | Cur: (${curPres}) | prev: (${lastPres})", "info", true)
		}

		storeExecutionHistory((now()-execTime), "watchDogCheck")
	}
}

Boolean checkOnline(dni){
	def d1=parent.getDevice(dni)
	if(d1){
		def curOnline=d1?.currentOnlineStatus?.toString()
		if(curOnline != "online"){
			watchDogAlarmActions(d1.displayName, dni, "online")
			//LogAction("watchDogCheck: | ${d1?.displayName} is not online | CurOnline: (${curOnline})", "warn", true)
			return false
		}
		return true
	}
	return false
}


private void watchDogAlarmActions(dev, String dni, String actType, p1=null, p2=null){
	String pName=watchDogPrefix()
	String evtNotifMsg=""
	String eventType="Warning"
	Integer lvl
	switch(actType){
		case "temp":
			evtNotifMsg="Safety Temp exceeded on ${dev}."
			break
		case "eco":
			if(settings["thermMissedEco"] != false){
				evtNotifMsg="Nest Location Home/Away Mode is 'Away' and thermostat [${dev}] is not in ECO."
			}else{return}
			break
		case "online":
			if(settings["onlineStatMon"] != false){
				evtNotifMsg="Device offline ${dev}."
			}else{return}
			break
		case "stream":
			evtNotifMsg="Camera streaming changed for ${dev} New: ${p1} Old:${p2}."
			lvl=3
			break
		case "locPres":
			evtNotifMsg="${dev} Nest Location has changed New: ${p1} Old: ${p2}."
			lvl=2
			eventType="Info"
			break
	}
	Boolean allowNotif=!!settings["${pName}NotifOn"]
	Boolean canNotif=(allowNotif && (getWatDogSafetyAlertDtSec(dni) > getWatDogRepeatMsgDelayVal()))
	if(canNotif){
		sendNofificationMsg(evtNotifMsg, eventType, pName, lvl) // this uses parent
		Boolean allowAlarm=allowNotif && settings."${pName}AllowAlarmNotif"
		if(allowAlarm){
			scheduleAlarmOn(pName)
		}
		state."watDogSafetyAlDt${dni}"=getDtNow()
	}
	String t0=eventType == "Info" ? "info" : "warn"
	LogAction("watchDogAlarmActions() | SENT: ${canNotif} | ${evtNotifMsg}", t0, true)
}

//def getWatDogSafetyAlertDtSec(dni){ return !state."watDogSafetyAlDt${dni}" ? 10000 : GetTimeDiffSeconds(state."watDogSafetyAlDt${dni}", null, "getWatDogSafetyAlertDtSec").toInteger() }
Integer getWatDogSafetyAlertDtSec(String dni){ return getTimeSeconds("watDogSafetyAlDt${dni}", 10000, "getWatDogSafetyAlertDtSec") }
Integer getWatDogRepeatMsgDelayVal(){ return !watDogRepeatMsgDelay ? 3600 : watDogRepeatMsgDelay.toInteger() }

Boolean isWatchdogConfigured(){
	return state.autoTyp=="watchDog"
}

/////////////////////THERMOSTAT AUTOMATION CODE LOGIC ///////////////////////

/****************************************************************************
|					REMOTE SENSOR AUTOMATION CODE							|
*****************************************************************************/

static String remSenPrefix(){ return "remSen" }

void removeVstat(callerStr){
	String autoType=getAutoType()
	if(autoType == "schMot"){
		String mycallerStr="${callerStr} removeVstat: Could "
		String t0=mycallerStr
		String myID=getMyLockId()
		if(!myID){
			setMyLockId(app.id)
			myID=getMyLockId()
		}
		def toRemove=state.remSenTstat
		if(settings.schMotTstat && myID && parent && toRemove){
			if(!parent?.addRemoveVthermostat(toRemove, false, myID)){
				t0 += "NOT "
			}
			t0 += "cleanup virtual thermostat\n"

			state.oldremSenTstat=state.remSenTstat
			state.remSenTstat=null

			t0 += mycallerStr
			if( !parent?.remSenUnlock(toRemove, myID) ){ // attempt unlock old ID
				t0 += "NOT "
			}
			LogAction(t0+'Release remote sensor lock', "info", false)
		}
	}
}

//Requirements Section
Boolean remSenCoolTempsReq()	{ return (settings.remSenRuleType in ["Cool", "Heat_Cool", "Cool_Circ", "Heat_Cool_Circ"]) }
Boolean remSenHeatTempsReq()	{ return (settings.remSenRuleType in ["Heat", "Heat_Cool", "Heat_Circ", "Heat_Cool_Circ"]) }
Boolean remSenDayHeatTempOk()	{ return (!remSenHeatTempsReq() || (remSenHeatTempsReq() && remSenDayHeatTemp)) }
Boolean remSenDayCoolTempOk()	{ return (!remSenCoolTempsReq() || (remSenCoolTempsReq() && remSenDayCoolTemp)) }

Boolean isRemSenConfigured(){
	Boolean devOk= !!(settings.remSensorDay)
	return settings.schMotRemoteSensor && devOk && settings.remSenRuleType && remSenDayHeatTempOk() && remSenDayCoolTempOk() 
}

Integer getMotionActiveSec(mySched){
	String sLbl="schMot_${mySched}_"
	return !state."${sLbl}MotionActiveDt" ? 0 : GetTimeDiffSeconds(state."${sLbl}MotionActiveDt", null, "getMotionActiveSec").toInteger()
}

Integer getMotionInActiveSec(mySched){
	String sLbl="schMot_${mySched}_"
	return !state."${sLbl}MotionInActiveDt" ? 0 : GetTimeDiffSeconds(state."${sLbl}MotionInActiveDt", null, "getMotionInActiveSec").toInteger()
}

void automationMotionEvt(evt){
	Long startTime=now()
	Long eventDelay=startTime - evt.date.getTime()
	LogAction("${evt.name.toUpperCase()} Event | Device: '${evt?.displayName}' | Motion: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "info", false)
	if(getIsAutomationDisabled()){ return }
	else {
		storeLastEventData(evt)
		Boolean dorunIn=false
		Integer delay=120
		String sLbl

		Integer mySched=getCurrentSchedule()
		List schedList=getScheduleList()
		String schName=""
		for (Integer cnt in schedList){
			sLbl="schMot_${cnt}_"
			Boolean act=settings["${sLbl}SchedActive"]

			if(act && settings["${sLbl}Motion"]){
				String str=settings["${sLbl}Motion"].toString()
				if(str.contains( evt.displayName)){
					Boolean oldActive=state."${sLbl}oldMotionActive"
					Boolean newActive=isMotionActive(settings["${sLbl}Motion"])
					state."${sLbl}oldMotionActive"=newActive
					if(oldActive != newActive){
						if(newActive){
							if(cnt == mySched){ delay=settings."${sLbl}MDelayValOn"?.toInteger() ?: 60 }
							state."${sLbl}MotionActiveDt"=getDtNow()
						}else{
							if(cnt == mySched){ delay=settings."${sLbl}MDelayValOff"?.toInteger() ?: 30*60 }
							state."${sLbl}MotionInActiveDt"=getDtNow()
						}
					}
					LogAction("Updating Schedule Motion Sensor State | Schedule: (${cnt} - ${getSchedLbl(cnt)}) | Previous Active: (${oldActive}) | Current Status: ($newActive)", "info", false)
					if(cnt == mySched){ dorunIn=true }
				}
			}
		}
/*
		if(settings["${sLbl}MPresHome"] || settings["${sLbl}MPresAway"]){
			if(settings["${sLbl}MPresHome"]){ if(!isSomebodyHome(settings["${sLbl}MPresHome"])) { dorunIn=false } }
			if(settings["${sLbl}MPresAway"]){ if(isSomebodyHome(settings["${sLbl}MPresAway"])) { dorunIn=false } }
		}
*/
		if(dorunIn){
			Integer val=Math.min( Math.max(delay,defaultAutomationTime()), 60)
			LogTrace("Automation Schedule Motion | Scheduling Delay Check: ($delay sec) | adjusted (${val}) | Schedule: ($mySched - ${getSchedLbl(mySched)})")
			scheduleAutomationEval(val)
		}else{
			def str="Motion Event | Skipping Motion Check: "
			if(mySched){
				str += "Motion Sensor is Not Used in Active Schedule (#${mySched} - ${getSchedLbl(getCurrentSchedule())})"
			}else{
				str += "No Active Schedule"
			}
			LogTrace(str)
		}
	}
}

Boolean isMotionActive(sensors){
	Boolean result=false
	sensors?.each { sen ->
		if(sen){
			String sval=sen?.currentMotion
			if(sval == "active"){ result=true }
		}
	}
	return result
}

Double getDeviceVarAvg(items, var){
	def tmpAvg=[]
	Double tempVal=0.0
	if(!items){ return tempVal }
	else {
		tmpAvg=items*."${var}"
		if(tmpAvg && tmpAvg?.size() > 0){ tempVal=(tmpAvg?.sum().toDouble() / tmpAvg?.size().toDouble()).round(1) }
	}
	return tempVal
}

Double getDeviceTempAvg(items){
	return  getDeviceVarAvg(items, "currentTemperature")
}

Double getDeviceTemp(dev){
	return  getDeviceVarAvg(dev, "currentTemperature")
}

def remSenShowTempsPage(){
	dynamicPage(name: "remSenShowTempsPage", uninstall: false){
		if(settings.remSensorDay){
			String t0=tUnitStr()
			section("Default Sensor Temps: (Schedules can override)"){
				Integer cnt=0
				Integer rCnt=settings.remSensorDay.size()
				String str=""
				str += "Sensor Temp (average): (${getDeviceTempAvg(settings.remSensorDay)}${t0})\n│"
				settings.remSensorDay?.each { t ->
					cnt=cnt+1
					str += "${(cnt >= 1) ? "${(cnt == rCnt) ? "\n└" : "\n├"}" : "\n└"} ${t?.label}: ${(t?.label?.toString()?.length() > 10) ? "\n${(rCnt == 1 || cnt == rCnt) ? "    " : "│"}└ " : ""}(${getDeviceTemp(t)}${t0})"
				}
				paragraph imgTitle(getAppImg("i_t"), sectionTitleStr(str)), state: "complete"
			}
		}
	}
}

Boolean remSendoSetCool(chgval, onTemp, offTemp){
	def remSenTstat=settings.schMotTstat
	def remSenTstatMir=settings.schMotTstatMir

	try {
		String hvacMode=remSenTstat ? remSenTstat?.currentThermostatMode?.toString() : (String)null
		def curCoolSetpoint=getTstatSetpoint(remSenTstat, "cool")
		def curHeatSetpoint=getTstatSetpoint(remSenTstat, "heat")
		def tempChangeVal=!remSenTstatTempChgVal ? 5.0 : Math.min(Math.max(remSenTstatTempChgVal.toDouble(), 2.0), 5.0)
		def maxTempChangeVal=tempChangeVal * 3

		chgval=(chgval > (onTemp + maxTempChangeVal)) ? onTemp + maxTempChangeVal : chgval
		chgval=(chgval < (offTemp - maxTempChangeVal)) ? offTemp - maxTempChangeVal : chgval

		String t0=tUnitStr()

		if(chgval != curCoolSetpoint){
			scheduleAutomationEval(70)
			def cHeat=null
			if(hvacMode in ["auto"]){
				if(curHeatSetpoint >= (offTemp-tempChangeVal)){
					cHeat=offTemp - tempChangeVal
					LogAction("Remote Sensor: HEAT - Adjusting HeatSetpoint to (${cHeat}${t0}) to allow COOL setting", "info", false)
					if(remSenTstatMir){ remSenTstatMir*.setHeatingSetpoint(cHeat) }
				}
			}
			if(setTstatAutoTemps(remSenTstat, chgval, cHeat, "remSen")){
				//LogAction("Remote Sensor: COOL - Adjusting CoolSetpoint to (${chgval}${t0}) ", "info", true)
				//storeLastAction("Adjusted Cool Setpoint to (${chgval}${t0}) Heat Setpoint to (${cHeat}${t0})", getDtNow(), "remSen")
				if(remSenTstatMir){ remSenTstatMir*.setCoolingSetpoint(chgval) }
			}
			return true // let all this take effect
		}else{
			LogAction("Remote Sensor: COOL - CoolSetpoint is already (${chgval}${t0}) ", "info", false)
		}

	} catch (ex){
		log.error "remSendoSetCool Exception: ${ex?.message}"
	}
	return false
}

Boolean remSendoSetHeat(chgval, onTemp, offTemp){
	def remSenTstat=settings.schMotTstat
	def remSenTstatMir=settings.schMotTstatMir

	try {
		String hvacMode=remSenTstat ? remSenTstat?.currentThermostatMode?.toString() : (String)null
		def curCoolSetpoint=getTstatSetpoint(remSenTstat, "cool")
		def curHeatSetpoint=getTstatSetpoint(remSenTstat, "heat")
		def tempChangeVal=!remSenTstatTempChgVal ? 5.0 : Math.min(Math.max(remSenTstatTempChgVal.toDouble(), 2.0), 5.0)
		def maxTempChangeVal=tempChangeVal * 3

		chgval=(chgval < (onTemp - maxTempChangeVal)) ? onTemp - maxTempChangeVal : chgval
		chgval=(chgval > (offTemp + maxTempChangeVal)) ? offTemp + maxTempChangeVal : chgval

		String t0=tUnitStr()

		if(chgval != curHeatSetpoint){
			scheduleAutomationEval(70)
			def cCool=null
			if(hvacMode in ["auto"]){
				if(curCoolSetpoint <= (offTemp+tempChangeVal)){
					cCool=offTemp + tempChangeVal
					LogAction("Remote Sensor: COOL - Adjusting CoolSetpoint to (${cCool}${t0}) to allow HEAT setting", "info", false)
					if(remSenTstatMir){ remSenTstatMir*.setCoolingSetpoint(cCool) }
				}
			}
			if(setTstatAutoTemps(remSenTstat, cCool, chgval, "remSen")){
				//LogAction("Remote Sensor: HEAT - Adjusting HeatSetpoint to (${chgval}${t0})", "info", false)
				//storeLastAction("Adjusted Heat Setpoint to (${chgval}${t0}) Cool Setpoint to (${cCool}${t0})", getDtNow(), "remSen")
				if(remSenTstatMir){ remSenTstatMir*.setHeatingSetpoint(chgval) }
			}
			return true // let all this take effect
		}else{
			LogAction("Remote Sensor: HEAT - HeatSetpoint is already (${chgval}${t0})", "info", false)
		}

	} catch (ex){
		log.error "remSendoSetHeat Exception: ${ex?.message}"
	}
	return false
}

/*
def getRemSenModeOk(){
	def result=false
	if(settings.remSensorDay ){ result=true }
	//log.debug "getRemSenModeOk: $result"
	return result
}
*/

void remSenCheck(){
	LogTrace("remSenCheck")
	if(getIsAutomationDisabled()){ return }
	try {
		def remSenTstat=settings.schMotTstat
		def remSenTstatMir=settings.schMotTstatMir

		Long execTime=now()

		String noGoDesc=""
		if( !settings.remSensorDay || !remSenTstat){
			noGoDesc += !settings.remSensorDay ? "Missing Required Sensor Selections" : ""
			noGoDesc += !remSenTstat ? "Missing Required Thermostat device" : ""
			LogTrace("Remote Sensor NOT Evaluating Status: ${noGoDesc}")
		}else{
			//log.info "remSenCheck: Evaluating Event"

			String tempScaleStr=tUnitStr()
			String hvacMode=remSenTstat ? remSenTstat.currentThermostatMode?.toString() : null
			if(hvacMode in [ "off", "eco"] ){
				LogAction("Remote Sensor: Skipping Evaluation; The Current Thermostat Mode is '${strCapitalize(hvacMode)}'", "info", false)
				disableOverrideTemps()
				storeExecutionHistory((now() - execTime), "remSenCheck")
				return
			}

			def reqSenHeatSetPoint=getRemSenHeatSetTemp(hvacMode)
			def reqSenCoolSetPoint=getRemSenCoolSetTemp(hvacMode)
			def threshold=getRemoteSenThreshold()

			if(hvacMode in ["auto"]){
				// check that requested setpoints make sense & notify
				def coolheatDiff=Math.abs(reqSenCoolSetPoint - reqSenHeatSetPoint)
				if( !((reqSenCoolSetPoint > reqSenHeatSetPoint) && (coolheatDiff >= 2)) ){
					LogAction("remSenCheck: Invalid Setpoints with auto mode: (${reqSenCoolSetPoint})/(${reqSenHeatSetPoint}, ${threshold})", "warn", true)
					storeExecutionHistory((now() - execTime), "remSenCheck")
					return
				}
			}

			def tempChangeVal=!remSenTstatTempChgVal ? 5.0 : Math.min(Math.max(remSenTstatTempChgVal.toDouble(), 2.0), 5.0)
			def maxTempChangeVal=tempChangeVal * 3
			def curTstatTemp=getDeviceTemp(remSenTstat).toDouble()
			def curSenTemp=getRemoteSenTemp().toDouble()

			String curTstatOperState=remSenTstat.currentThermostatOperatingState
			String curTstatFanMode=remSenTstat.currentThermostatFanMode
			Boolean fanOn=(curTstatFanMode == "on" || curTstatFanMode == "circulate")
			def curCoolSetpoint=getTstatSetpoint(remSenTstat, "cool")
			def curHeatSetpoint=getTstatSetpoint(remSenTstat, "heat")
			Boolean acRunning=(curTstatOperState == "cooling")
			Boolean heatRunning=(curTstatOperState == "heating")

/*
			LogAction("remSenCheck: Rule Type: ${getEnumValue(remSenRuleEnum("heatcool"), settings.remSenRuleType)}", "info", false)
			LogAction("remSenCheck: Sensor Temp: ${curSenTemp}", "info", false)
			LogAction("remSenCheck: Thermostat Info - ( Temperature: (${curTstatTemp}) | HeatSetpoint: (${curHeatSetpoint}) | CoolSetpoint: (${curCoolSetpoint}) | HvacMode: (${hvacMode}) | OperatingState: (${curTstatOperState}) | FanMode: (${curTstatFanMode}) )", "info", false)
			LogAction("remSenCheck: Desired Temps - Heat: ${reqSenHeatSetPoint} | Cool: ${reqSenCoolSetPoint}", "info", false)
			LogAction("remSenCheck: Threshold Temp: ${threshold} | Change Temp Increments: ${tempChangeVal}", "info", false)
*/

			Boolean chg=false
			def chgval=0
			if(hvacMode in ["cool","auto"]){
				//Changes Cool Setpoints
				if(settings.remSenRuleType in ["Cool", "Heat_Cool", "Heat_Cool_Circ"]){
					def onTemp=reqSenCoolSetPoint + threshold
					def offTemp=reqSenCoolSetPoint
					Boolean turnOn=false
					Boolean turnOff=false

					LogTrace("Remote Sensor: COOL - (Sensor Temp: ${curSenTemp} - CoolSetpoint: ${reqSenCoolSetPoint})")
					if(curSenTemp <= offTemp){
						turnOff=true
					}else if(curSenTemp >= onTemp){
						turnOn=true
					}

					if(turnOff && acRunning){
						chgval=curTstatTemp + tempChangeVal
						chg=true
						LogAction("Remote Sensor: COOL - Adjusting CoolSetpoint to Turn Off Thermostat", "info", false)
						acRunning=false
						state.remSenCoolOn=false
					}else if(turnOn && !acRunning){
						chgval=curTstatTemp - tempChangeVal
						chg=true
						acRunning=true
						state.remSenCoolOn=true
						LogAction("Remote Sensor: COOL - Adjusting CoolSetpoint to Turn On Thermostat", "info", false)
					}else{
						// logic to decide if we need to nudge thermostat to keep it on or off
						if(acRunning){
							chgval=curTstatTemp - tempChangeVal
							state.remSenCoolOn=true
						}else{
							chgval=curTstatTemp + tempChangeVal
							state.remSenCoolOn=false
						}
						def coolDiff1=Math.abs(curTstatTemp - curCoolSetpoint)
						//LogAction("Remote Sensor: COOL - coolDiff1: ${coolDiff1} tempChangeVal: ${tempChangeVal}", "info", false)
						if(coolDiff1 < (tempChangeVal / 2)){
							chg=true
							LogAction("Remote Sensor: COOL - Adjusting CoolSetpoint to maintain state", "info", false)
						}
					}
					if(chg){
						if(remSendoSetCool(chgval, onTemp, offTemp)){
							storeExecutionHistory((now() - execTime), "remSenCheck")
							return // let all this take effect
						}

					}
					//else { LogAction("Remote Sensor: NO CHANGE TO COOL - CoolSetpoint is (${curCoolSetpoint}${tempScaleStr}) ", "info", false) }
				}
			}

			chg=false
			chgval=0

			//LogAction("remSenCheck: Thermostat Info - ( Temperature: (${curTstatTemp}) | HeatSetpoint: (${curHeatSetpoint}) | CoolSetpoint: (${curCoolSetpoint}) | HvacMode: (${hvacMode}) | OperatingState: (${curTstatOperState}) | FanMode: (${curTstatFanMode}) )", "info", false)

			//Heat Functions.
			if(hvacMode in ["heat", "emergency heat", "auto"]){
				if(settings.remSenRuleType in ["Heat", "Heat_Cool", "Heat_Cool_Circ"]){
					def onTemp=reqSenHeatSetPoint - threshold
					def offTemp=reqSenHeatSetPoint
					Boolean turnOn=false
					Boolean turnOff=false

					//LogAction("Remote Sensor: HEAT - (Sensor Temp: ${curSenTemp} - HeatSetpoint: ${reqSenHeatSetPoint})", "info", false)
					if(curSenTemp <= onTemp){
						turnOn=true
					}else if(curSenTemp >= offTemp){
						turnOff=true
					}

					if(turnOff && heatRunning){
						chgval=curTstatTemp - tempChangeVal
						chg=true
						LogAction("Remote Sensor: HEAT - Adjusting HeatSetpoint to Turn Off Thermostat", "info", false)
						heatRunning=false
						state.remSenHeatOn=false
					}else if(turnOn && !heatRunning){
						chgval=curTstatTemp + tempChangeVal
						chg=true
						LogAction("Remote Sensor: HEAT - Adjusting HeatSetpoint to Turn On Thermostat", "info", false)
						state.remSenHeatOn=true
						heatRunning=true
					}else{
						// logic to decide if we need to nudge thermostat to keep it on or off
						if(heatRunning){
							chgval=curTstatTemp + tempChangeVal
							state.remSenHeatOn=true
						}else{
							chgval=curTstatTemp - tempChangeVal
							state.remSenHeatOn=false
						}
						def heatDiff1=Math.abs(curTstatTemp - curHeatSetpoint)
						//LogAction("Remote Sensor: HEAT - heatDiff1: ${heatDiff1} tempChangeVal: ${tempChangeVal}", "info", false)
						if(heatDiff1 < (tempChangeVal / 2)){
							chg=true
							LogAction("Remote Sensor: HEAT - Adjusting HeatSetpoint to maintain state", "info", false)
						}
					}
					if(chg){
						if(remSendoSetHeat(chgval, onTemp, offTemp)){
							storeExecutionHistory((now() - execTime), "remSenCheck")
							return // let all this take effect
						}
					}
					//else { LogAction("Remote Sensor: NO CHANGE TO HEAT - HeatSetpoint is already (${curHeatSetpoint}${tempScaleStr})", "info", false) }
				}
			}
		}
/*
			//
			// if all thermostats (primary and mirrors) are Nest, then AC/HEAT & fan may be off (or set back) with away mode. (depends on user's home/away assist settings in Nest)
			// if thermostats were not all Nest, then non Nest units could still be on for AC/HEAT or FAN
			// current presumption in this implementation is:
			//	they are all nests or integrated with Nest (Works with Nest) as we don't have away/home temps for each mirror thermostats.  (They could be mirrored from primary)
			//	all thermostats in an automation are in the same Nest structure, so that all share home/away settings
			//
*/
		storeExecutionHistory((now() - execTime), "remSenCheck")
	} catch (ex){
		log.error "remSenCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "remSenCheck", true, getAutoType())
	}
}

List getRemSenTempsToList(){
	Integer mySched=getCurrentSchedule()
	def sensors
	if(mySched){
		def sLbl="schMot_${mySched}_"
		if(settings["${sLbl}remSensor"]){
			sensors=settings["${sLbl}remSensor"]
		}
	}
	if(!sensors){ sensors=settings.remSensorDay }
	if(sensors?.size() >= 1){
		String t0=tUnitStr()
		List info=[]
		sensors?.sort().each {
			info.push("${it?.displayName}": " ${it?.currentTemperature.toString()}${t0}")
		}
		return info
	}
	return null
}

def getTstatSetpoint(tstat, type){
	if(tstat){
		if(type == "cool"){
			def coolSp=tstat?.currentCoolingSetpoint
			//log.debug "getTstatSetpoint(cool): $coolSp"
			return coolSp ? coolSp?.toDouble() : 0
		}else{
			def heatSp=tstat?.currentHeatingSetpoint
			//log.debug "getTstatSetpoint(heat): $heatSp"
			return heatSp ? heatSp?.toDouble() : 0
		}
	}
	else { return 0 }
}

def getRemoteSenThreshold(){
	def threshold=settings.remSenTempDiffDegrees
	Integer mySched=getCurrentSchedule()
	if(mySched){
		def sLbl="schMot_${mySched}_"
		if(settings["${sLbl}remSenThreshold"]){
			threshold=settings["${sLbl}remSenThreshold"]
		}
	}
	def theMin=getTemperatureScale() == "C" ? 0.3 : 0.6
	threshold=!threshold ? 2.0 : Math.min(Math.max(threshold.toDouble(),theMin), 4.0)
	return threshold.toDouble()
}

def getRemoteSenTemp(){
	Integer mySched=getCurrentSchedule()
	if(state.remoteTempSourceStr != null){ state.remoteTempSourceStr=null }
	if(state.currentSchedNum != null){ state.currentSchedNum=null }
	def sens
	if(mySched){
		String sLbl="schMot_${mySched}_"
		if(settings["${sLbl}remSensor"]){
			state.remoteTempSourceStr="Schedule"
			state.currentSchedNum=mySched
			sens=settings["${sLbl}remSensor"]
			return getDeviceTempAvg(sens).toDouble()
		}
	}
	if(isRemSenConfigured()){
		state.remoteTempSourceStr="Remote Sensor"
		state.currentSchedNum=null
		return getDeviceTempAvg(settings.remSensorDay).toDouble()
	}else{
		state.remoteTempSourceStr="Thermostat"
		state.currentSchedNum=null
		return getDeviceTemp(settings.schMotTstat).toDouble()
/*
	else {
		LogAction("getRemoteSenTemp: No Temperature Found!", "warn", true)
		return 0.0
*/
	}
}

def fixTempSetting(temp){
	Double newtemp=temp?.toDouble()
	if(temp != null){
		if(getTemperatureScale() == "C"){
			if(temp > 35){    // setting was done in F
				newtemp=roundTemp( ((newtemp - 32.0) * (5 / 9)) as Double)
			}
		}else if(getTemperatureScale() == "F"){
			if(temp < 40){    // setting was done in C
				newtemp=roundTemp( (((newtemp * (9 / 5)) as Double) + 32.0) ).toInteger()
			}
		}
	}
	return newtemp
}

def setRemoteSenTstat(val){
	LogAction("setRemoteSenTstat $val", "info", false)
	state.remSenTstat=val
}

def getRemSenCoolSetTemp(String curMode=null, Boolean isEco=false, Boolean useCurrent=true){
	Double coolTemp
	String theMode=curMode != null ? curMode : null
	if(theMode == null){
		def tstat=settings.schMotTstat
		theMode=tstat ? tstat?.currentThermostatMode.toString() : null
	}
	state.remoteCoolSetSourceStr=""
	if(theMode != "eco"){
		if(getOverrideCoolSec() < (3600 * 4)){
			if(state.remSenCoverride != null){
				coolTemp=fixTempSetting(state.remSenCoverride.toDouble())
				state.remoteCoolSetSourceStr="Remote Sensor Override"
			}
		}else{ state.remSenCoverride=null }

		if(coolTemp == null){
			Integer mySched=getCurrentSchedule()
			if(mySched){
				def useMotion=state."motion${mySched}UseMotionSettings"
				def hvacSettings=state."sched${mySched}restrictions"
				coolTemp=!useMotion ? hvacSettings?.ctemp : hvacSettings?.mctemp ?: hvacSettings?.ctemp
				state.remoteCoolSetSourceStr="Schedule"
			}
// ERS if Remsensor is enabled
			if(isRemSenConfigured()){
				if(theMode == "cool" && coolTemp == null /* && isEco */){
					if(state.extTmpSavedTemp){
						coolTemp=state.extTmpSavedTemp.toDouble()
						state.remoteCoolSetSourceStr="Last Desired Temp"
					}
				}
				if(theMode == "auto" && coolTemp == null /* && isEco */){
					if(state.extTmpSavedCTemp){
						coolTemp=state.extTmpSavedCTemp.toDouble()
						state.remoteCoolSetSourceStr="Last Desired CTemp"
					}
				}

				if(coolTemp == null && remSenDayCoolTemp){
					coolTemp=remSenDayCoolTemp.toDouble()
					state.remoteCoolSetSourceStr="RemSen Day Cool Temp"
				}

				if(coolTemp == null){
					def desiredCoolTemp=getGlobalDesiredCoolTemp()
					if(desiredCoolTemp){
						coolTemp=desiredCoolTemp.toDouble()
						state.remoteCoolSetSourceStr="Global Desired Cool Temp"
					}
				}

				if(coolTemp){
					coolTemp=fixTempSetting(coolTemp)
				}
			}
		}
	}
	if(coolTemp == null && useCurrent){
		coolTemp=settings.schMotTstat ? getTstatSetpoint(settings.schMotTstat, "cool") : coolTemp
		state.remoteCoolSetSourceStr="Thermostat"
	}
	return coolTemp
}

def getRemSenHeatSetTemp(curMode=null, isEco=false, useCurrent=true){
	Double heatTemp
	def theMode=curMode != null ? curMode : null
	if(theMode == null){
		def tstat=settings.schMotTstat
		theMode=tstat ? tstat?.currentThermostatMode.toString() : null
	}
	state.remoteHeatSetSourceStr=""
	if(theMode != "eco"){
		if(getOverrideHeatSec() < (3600 * 4)){
			if(state.remSenHoverride != null){
				heatTemp=fixTempSetting(state.remSenHoverride.toDouble())
				state.remoteHeatSetSourceStr="Remote Sensor Override"
			}
		}else{ state.remSenHoverride=null }

		if(heatTemp == null){
			Integer mySched=getCurrentSchedule()
			if(mySched){
				Boolean useMotion=state."motion${mySched}UseMotionSettings"
				def hvacSettings=state."sched${mySched}restrictions"
				heatTemp=!useMotion ? hvacSettings.htemp : hvacSettings.mhtemp ?: hvacSettings.htemp
				state.remoteHeatSetSourceStr="Schedule"
			}
// ERS if Remsensor is enabled
			if(isRemSenConfigured()){
				if(theMode == "heat" && heatTemp == null /* && isEco */){
					if(state.extTmpSavedTemp){
						heatTemp=state.extTmpSavedTemp.toDouble()
						state.remoteHeatSetSourceStr="Last Desired Temp"
					}
				}
				if(theMode == "auto" && heatTemp == null /* && isEco */){
					if(state.extTmpSavedHTemp){
						heatTemp=state.extTmpSavedHTemp.toDouble()
						state.remoteHeatSetSourceStr="Last Desired HTemp"
					}
				}

				if(heatTemp == null && remSenDayHeatTemp){
					heatTemp=remSenDayHeatTemp.toDouble()
					state.remoteHeatSetSourceStr="RemSen Day Heat Temp"
				}

				if(heatTemp == null){
					def desiredHeatTemp=getGlobalDesiredHeatTemp()
					if(desiredHeatTemp){
						heatTemp=desiredHeatTemp.toDouble()
						state.remoteHeatSetSourceStr="Global Desired Heat Temp"
					}
				}

				if(heatTemp){
					heatTemp=fixTempSetting(heatTemp)
				}
			}
		}
	}

	if(heatTemp == null && useCurrent){
		heatTemp=settings.schMotTstat ? getTstatSetpoint(settings.schMotTstat, "heat") : heatTemp
		state.remoteHeatSetSourceStr="Thermostat"
	}
	return heatTemp
}


//  When a temp change is sent to virtual device, it lasts for 4 hours, next turn off, or next schedule change, then we return to automation settings
// Other choices could be to change the schedule setpoint permanently if one is active, or allow folks to set timer

Integer getOverrideCoolSec(){ return !state.remSenCoverrideDt ? 100000 : GetTimeDiffSeconds(state.remSenCoverrideDt, null, "getOverrideCoolSec").toInteger() }
Integer getOverrideHeatSec(){ return !state.remSenHoverrideDt ? 100000 : GetTimeDiffSeconds(state.remSenHoverrideDt, null, "getOverrideHeatSec").toInteger() }

void disableOverrideTemps(){
	if(state.remSenHoverride || state.remSenCoverride){
		stateRemove("remSenCoverride")
		stateRemove("remSenHoverride")
		stateRemove("remSenCoverrideDt")
		stateRemove("remSenHoverrideDt")
		LogAction("disableOverrideTemps: Disabling Override temps", "info", false)
	}
}

Boolean remSenTempUpdate(temp, mode){
	//LogAction("remSenTempUpdate(${temp}, ${mode})", "info", false)

	Boolean res=false
	if(getIsAutomationDisabled()){ return res }
	switch(mode){
		case "heat":
			if(remSenHeatTempsReq()){
				//LogAction("remSenTempUpdate Set Heat Override to: ${temp} for 4 hours", "info", false)
				state.remSenHoverride=temp.toDouble()
				state.remSenHoverrideDt=getDtNow()
				res=true
			}
			break
		case "cool":
			if(remSenCoolTempsReq()){
				//LogAction("remSenTempUpdate Set Cool Override to: ${temp} for 4 hours", "info", false)
				state.remSenCoverride=temp.toDouble()
				state.remSenCoverrideDt=getDtNow()
				res=true
			}
			break
		default:
			LogAction("remSenTempUpdate Invalid Request: ${mode}, ${temp}", "warn", true)
			break
	}
	if(res){
		scheduleAutomationEval()
		LogAction("remSenTempUpdate Set ${mode} Override to: ${temp} for 4 hours", "info", false)
	}
	return res
}

Map remSenRuleEnum(String type=(String)null){
	// Determines that available rules to display based on the selected thermostats capabilites.
	Boolean canCool=state.schMotTstatCanCool
	Boolean canHeat=state.schMotTstatCanHeat
	Boolean hasFan=state.schMotTstatHasFan

	//log.debug "remSenRuleEnum -- hasFan: $hasFan (${state.schMotTstatHasFan} | canCool: $canCool (${state.schMotTstatCanCool} | canHeat: $canHeat (${state.schMotTstatCanHeat}"
	Map vals=[:]
	if(type){
		if(type == "fan"){
			vals=["Circ":"Eco/Circulate(Fan)"]
			if(canCool){ vals << ["Cool_Circ":"Cool/Circulate(Fan)"] }
			if(canHeat){ vals << ["Heat_Circ":"Heat/Circulate(Fan)"] }
			if(canHeat && canCool){ vals << [ "Heat_Cool_Circ":"Auto/Circulate(Fan)"] }
		}
		else if(type == "heatcool"){
			if(!canCool && canHeat){ vals=["Heat":"Heat"] }
			else if(canCool && !canHeat){ vals=["Cool":"Cool"] }
			else { vals=["Heat_Cool":"Auto", "Heat":"Heat", "Cool":"Cool"] }
		}
		else { LogAction("remSenRuleEnum: Invalid Type ($type)", "error", true) }
	}
	else {
		if(canCool && !canHeat && hasFan){ vals=["Cool":"Cool", "Circ":"Eco/Circulate(Fan)", "Cool_Circ":"Cool/Circulate(Fan)"] }
		else if(canCool && !canHeat && !hasFan){ vals=["Cool":"Cool"] }
		else if(!canCool && canHeat && hasFan){ vals=["Circ":"Eco/Circulate(Fan)", "Heat":"Heat", "Heat_Circ":"Heat/Circulate(Fan)"] }
		else if(!canCool && canHeat && !hasFan){ vals=["Heat":"Heat"] }
		else if(!canCool && !canHeat && hasFan){ vals=["Circ":"Eco/Circulate(Fan)"] }
		else if(canCool && canHeat && !hasFan){ vals=["Heat_Cool":"Auto", "Heat":"Heat", "Cool":"Cool"] }
		else { vals=[ "Heat_Cool":"Auto", "Heat":"Heat", "Cool":"Cool", "Circ":"Eco/Circulate(Fan)", "Heat_Cool_Circ":"Auto/Circulate(Fan)", "Heat_Circ":"Heat/Circulate(Fan)", "Cool_Circ":"Cool/Circulate(Fan)" ] }
	}
	//log.debug "remSenRuleEnum vals: $vals"
	return vals
}

/************************************************************************
|					FAN CONTROL AUTOMATION CODE					|
*************************************************************************/

static String fanCtrlPrefix(){ return "fanCtrl" }

Boolean isFanCtrlConfigured(){
	return settings.schMotOperateFan && (isFanCtrlSwConfigured() || isFanCircConfigured())
}

Boolean isFanCtrlSwConfigured(){
	return settings.schMotOperateFan && settings.fanCtrlFanSwitches && settings.fanCtrlFanSwitchTriggerType && settings.fanCtrlFanSwitchHvacModeFilter
}

Boolean isFanCircConfigured(){
	return settings.schMotOperateFan && (settings.schMotCirculateTstatFan || settings.schMotCirculateExtFan) && settings.schMotFanRuleType
}

String getFanSwitchDesc(Boolean showOpt=true){
	String swDesc=""
	Integer swCnt=0
	String pName=fanCtrlPrefix()
	if(showOpt){
		swDesc += (settings."${pName}FanSwitches" && (settings."${pName}FanSwitchSpeedCtrl" || settings."${pName}FanSwitchTriggerType" || settings."${pName}FanSwitchHvacModeFilter")) ? "Fan Switch Config:" : ""
	}
	swDesc += settings."${pName}FanSwitches" ? "${showOpt ? "\n" : ""}• Fan Switches:" : ""
	Integer rmSwCnt=settings."${pName}FanSwitches"?.size() ?: 0
	settings."${pName}FanSwitches"?.sort { it?.displayName }?.each { sw ->
		swCnt=swCnt+1
		swDesc += "${swCnt >= 1 ? "${swCnt == rmSwCnt ? "\n   └" : "\n   ├"}" : "\n   └"} ${sw?.label}: (${strCapitalize(sw?.currentSwitch)})"
		swDesc += checkFanSpeedSupport(sw) ? "\n	 └ Current Spd: (${sw?.currentSpeed?.toString()})" : ""
	}
	if(showOpt){
		if(settings."${pName}FanSwitches"){
			swDesc += (settings."${pName}FanSwitchSpeedCtrl" || settings."${pName}FanSwitchTriggerType" || settings."${pName}FanSwitchHvacModeFilter") ? "\n\nFan Triggers:" : ""
			swDesc += (settings."${pName}FanSwitchSpeedCtrl") ? "\n • Fan Speed Support: (Active)" : ""
			swDesc += (settings."${pName}FanSwitchTriggerType") ? "\n • Fan Trigger:\n   └(${getEnumValue(switchRunEnum(), settings."${pName}FanSwitchTriggerType")})" : ""
			swDesc += (settings."${pName}FanSwitchHvacModeFilter") ? "\n • Hvac Mode Filter:\n   └(${getEnumValue(fanModeTrigEnum(), settings."${pName}FanSwitchHvacModeFilter")})" : ""
		}
	}

	Boolean t0=isFanCircConfigured()
	swDesc += (t0) ? "\n\nFan Circulation Enabled:" : ""
	swDesc += (t0) ? "\n • Fan Circulation Rule:\n   └(${getEnumValue(remSenRuleEnum("fan"), settings.schMotFanRuleType)})" : ""
	swDesc += (t0 && settings.fanCtrlTempDiffDegrees) ? ("\n • Threshold: (${settings.fanCtrlTempDiffDegrees}${tUnitStr()})") : ""
	swDesc += (t0 && settings.fanCtrlOnTime) ? ("\n • Circulate Time: (${getEnumValue(fanTimeSecEnum(), settings.fanCtrlOnTime)})") : ""
	swDesc += (t0 && settings.fanCtrlTimeBetweenRuns) ? ("\n • Time Between Cycles:\n   └ (${getEnumValue(longTimeSecEnum(), settings.fanCtrlTimeBetweenRuns)})") : ""

	swDesc += (settings."${pName}FanSwitches" || t0) ? "\n\nRestrictions Active: (${autoScheduleOk(fanCtrlPrefix()) ? "No" : "Yes"})" : ""

	return (swDesc == "") ? null : "${swDesc}"
}

Boolean getFanSwitchesSpdChk(){
	Integer devCnt=0
	String pName=fanCtrlPrefix()
	if(settings."${pName}FanSwitches"){
		settings."${pName}FanSwitches"?.each { sw ->
			if(checkFanSpeedSupport(sw)){ devCnt=devCnt+1 }
		}
	}
	return devCnt>0
}

Boolean fanCtrlScheduleOk(){ return autoScheduleOk(fanCtrlPrefix()) }

void fanCtrlCheck(){
	//LogAction("FanControl Event | Fan Switch Check", "info", false)
	try {
		def fanCtrlTstat=settings.schMotTstat

		if(getIsAutomationDisabled()){ return }
		if( !isFanCtrlConfigured()){ return }

		Long execTime=now()
		//state.autoRunDt=getDtNow()

		String curMode=settings.schMotTstat ? settings.schMotTstat?.currentThermostatMode?.toString() : null
		Boolean modeEco= (curMode in ["eco"])

		def reqHeatSetPoint
		def reqCoolSetPoint
		if(!modeEco){
			reqHeatSetPoint=getRemSenHeatSetTemp(curMode)
			reqCoolSetPoint=getRemSenCoolSetTemp(curMode)
		}

		String lastMode=settings.schMotTstat ? settings.schMotTstat?.currentpreviousthermostatMode?.toString() : null
		if(!lastMode && modeEco && isRemSenConfigured()){
			if( /* !lastMode && */ state.extTmpTstatOffRequested && state.extTmplastMode){
				lastMode=state.extTmplastMode
			}
		}
		if(lastMode){
			if(!reqHeatSetpoint){ reqHeatSetPoint=getRemSenHeatSetTemp(lastMode, modeEco, false) }
			if(!reqCoolSetpoint){ reqCoolSetPoint=getRemSenCoolSetTemp(lastMode, modeEco, false) }
			if(isRemSenConfigured()){
				if(!reqHeatSetPoint){ reqHeatSetPoint=state.extTmpSavedHTemp }
				if(!reqCoolSetPoint){ reqCoolSetPoint=state.extTmpSavedCTemp }
			}
			LogAction("fanCtrlCheck: Using lastMode: ${lastMode} | extTmpTstatOffRequested: ${state.extTmpTstatOffRequested} | curMode: ${curMode}", "info", false)
		}

		reqHeatSetPoint=reqHeatSetPoint ?: 0
		reqCoolSetPoint=reqCoolSetPoint ?: 0

		def curTstatTemp=getRemoteSenTemp().toDouble()

		def t0=getReqSetpointTemp(curTstatTemp, reqHeatSetPoint, reqCoolSetPoint).req
		def curSetPoint=t0 ? t0.toDouble() : 0

		def tempDiff=Math.abs(curSetPoint - curTstatTemp)
		LogAction("fanCtrlCheck: Desired Temps - Heat: ${reqHeatSetPoint} | Cool: ${reqCoolSetPoint}", "info", false)
		LogAction("fanCtrlCheck: Current Thermostat Sensor Temp: ${curTstatTemp} Temp Difference: (${tempDiff})", "info", false)

		Boolean circWantsOn=null
		if(isFanCircConfigured()){
			Double adjust=(getTemperatureScale() == "C") ? 0.5 : 1.0
			Double threshold=!settings.fanCtrlTempDiffDegrees ? adjust : settings.fanCtrlTempDiffDegrees.toDouble()
			String hvacMode=curMode
/*
			def curTstatFanMode=settings.schMotTstat?.currentThermostatFanMode.toString()
			def fanOn= curTstatFanMode == "on" || curTstatFanMode == "circulate"
			if(state.haveRunFan){
				if(schMotFanRuleType in ["Circ", "Cool_Circ", "Heat_Circ", "Heat_Cool_Circ"]){
					if(fanOn){
						LogAction("fantCtrlCheck: Turning OFF '${settings.schMotTstat?.displayName}' Fan; Modes do not match evaluation", "info", false)
						storeLastAction("Turned ${settings.schMotTstat} Fan to (Auto)", getDtNow(), "fanCtrl", settings.schMotTstat)
						settings.schMotTstat?.fanAuto()
						if(settings.schMotTstatMir){ settings.schMotTstatMir*.fanAuto() }
					}
				}
				state.haveRunFan=false
			}
*/
			def sTemp=getReqSetpointTemp(curTstatTemp, reqHeatSetPoint, reqCoolSetPoint)
			String resultMode=sTemp?.type?.toString()
			Boolean can_Circ=false
			if(
				!(hvacMode in ["off"]) && (
					( hvacMode in ["cool"] && schMotFanRuleType in ["Cool_Circ"]) ||
					( resultMode in ["cool"] && schMotFanRuleType in ["Cool_Circ", "Heat_Cool_Circ"]) ||
					( hvacMode in ["heat"] && schMotFanRuleType in ["Heat_Circ"]) ||
					( resultMode in ["heat"] && schMotFanRuleType in ["Heat_Circ", "Heat_Cool_Circ"]) ||
					( hvacMode in ["auto"] && schMotFanRuleType in ["Heat_Cool_Circ"]) ||
					( hvacMode in ["eco"] && schMotFanRuleType in ["Circ"])
				)
			){

				can_Circ=true
			}
			circWantsOn=circulateFanControl(resultMode, curTstatTemp, sTemp?.req?.toDouble(), threshold, can_Circ)
		}

		if(isFanCtrlSwConfigured()){
			doFanOperation(tempDiff, curTstatTemp, reqHeatSetPoint, reqCoolSetPoint, circWantsOn)
		}

		storeExecutionHistory((now()-execTime), "fanCtrlCheck")

	} catch (ex){
		log.error "fanCtrlCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "fanCtrlCheck", true, getAutoType())
	}
}

Map getReqSetpointTemp(curTemp, reqHeatSetPoint, reqCoolSetPoint){
	//LogAction("getReqSetpointTemp: Current Temp: ${curTemp} Req Heat: ${reqHeatSetPoint} Req Cool: ${reqCoolSetPoint}", "info", false)
	def tstat=settings.schMotTstat

		//def modeEco=(curMode == "eco")
		//def modeAuto=(curMode == "auto")

	Boolean canHeat=state.schMotTstatCanHeat
	Boolean canCool=state.schMotTstatCanCool
	String hvacMode=tstat ? tstat.currentThermostatMode.toString() : null
	String operState=tstat ? tstat.currentThermostatOperatingState.toString() : null
	String opType=hvacMode

	if(hvacMode == "off"){
		return ["req":null, "type":"off"]
	}
	if((hvacMode == "cool") || (operState == "cooling") || (hvacMode == "eco" && !canHeat && canCool) ){
		opType="cool"
	}else if((hvacMode == "heat") || (operState == "heating")|| (hvacMode == "eco" && !canCool && canHeat) ){
		opType="heat"
	}else if(hvacMode == "auto" || hvacMode == "eco"){
		def coolDiff=Math.abs(curTemp - reqCoolSetPoint)
		def heatDiff=Math.abs(curTemp - reqHeatSetPoint)
		opType=coolDiff < heatDiff ? "cool" : "heat"
	}
	def temp=(opType == "cool") ? reqCoolSetPoint?.toDouble() : reqHeatSetPoint?.toDouble()
	return ["req":temp, "type":opType]
}

def doFanOperation(tempDiff, curTstatTemp, curHeatSetpoint, curCoolSetpoint, Boolean circWantsOn){
	String pName=fanCtrlPrefix()
	//LogAction("doFanOperation: Temp Difference: (${tempDiff})", "info", false)
	try {
		def tstat=settings.schMotTstat

/*		def curTstatTemp=tstat ? getRemoteSenTemp().toDouble() : null
		def curCoolSetpoint=getRemSenCoolSetTemp()
		def curHeatSetpoint=getRemSenHeatSetTemp()
*/
		String hvacMode=tstat ? tstat?.currentThermostatMode.toString() : null
		String curTstatOperState=tstat?.currentThermostatOperatingState.toString()
		String curTstatFanMode=tstat?.currentThermostatFanMode.toString()
		//LogAction("doFanOperation: Thermostat Info - ( Temperature: (${curTstatTemp}) | HeatSetpoint: (${curHeatSetpoint}) | CoolSetpoint: (${curCoolSetpoint}) | HvacMode: (${hvacMode}) | OperatingState: (${curTstatOperState}) | FanMode: (${curTstatFanMode}) )", "info", false)

		if(state.haveRunFan == null){ state.haveRunFan=false }
		Boolean savedHaveRun=state.haveRunFan

		//def wantFanOn=circWantsOn != null ? circWantsOn ? false
		Boolean wantFanOn=false
//	1:"Heating/Cooling", 2:"With Fan Only", 3:"Heating", 4:"Cooling"

		List validOperModes=[]
		switch ( settings."${pName}FanSwitchTriggerType".toInteger() ){
			case 1:
				validOperModes=["heating", "cooling"]
				wantFanOn=(curTstatOperState in validOperModes)
				break
			case 2:
				wantFanOn=(curTstatFanMode in ["on", "circulate"])
				break
			case 3:
				validOperModes=["heating"]
				wantFanOn=(curTstatOperState in validOperModes)
				break
			case 4:
				validOperModes=["cooling"]
				wantFanOn=(curTstatOperState in validOperModes)
				break
			default:
				break
		}
/*
		if( settings."${pName}FanSwitchTriggerType".toInteger() == 1){
			def validOperModes=["heating", "cooling"]
			wantFanOn=(curTstatOperState in ["heating", "cooling"])
		}
		if( settings."${pName}FanSwitchTriggerType".toInteger() == 2){
			wantFanOn=(curTstatFanMode in ["on", "circulate"])
		}
		//if(settings."${pName}FanSwitchHvacModeFilter" != "any" && (settings."${pName}FanSwitchHvacModeFilter" != hvacMode)){
*/
		if( !( ("any" in settings."${pName}FanSwitchHvacModeFilter") || (hvacMode in settings."${pName}FanSwitchHvacModeFilter") )  ){
			if(savedHaveRun){
				LogAction("doFanOperation: Evaluating turn fans off; Thermostat Mode does not Match the required Mode", "info", false)
			}
			wantFanOn=false  // force off of fans
		}

		Boolean schedOk=fanCtrlScheduleOk()
		if(!schedOk){
			if(savedHaveRun){
				LogAction("doFanOperation: Evaluating turn fans off; Schedule is restricted", "info", false)
			}
			wantFanOn=false  // force off of fans
			circWantsOn=false  // force off of fans
		}

		Boolean allOff=true
		settings."${pName}FanSwitches"?.each { sw ->
			def swOn=(sw?.currentSwitch.toString() == "on")
			if(wantFanOn || circWantsOn){
				if(!swOn && !savedHaveRun){
					LogAction("doFanOperation: Fan Switch (${sw?.displayName}) is (${swOn ? "ON" : "OFF"}) | Turning '${sw}' Switch (ON)", "info", false)
					sw.on()
					swOn=true
					state.haveRunFan=true
					storeLastAction("Turned On $sw)", getDtNow(), pName)
				}else{
					if(!swOn && savedHaveRun){
						LogAction("doFanOperation: savedHaveRun state shows switch ${sw} turned OFF outside of automation requests", "info", false)
					}
				}
				if(swOn && state.haveRunFan && checkFanSpeedSupport(sw)){
					def t0=sw?.currentSpeed
					def speed=t0 ?: null
					if(settings."${pName}FanSwitchSpeedCtrl" && settings."${pName}FanSwitchHighSpeed" && settings."${pName}FanSwitchMedSpeed" && settings."${pName}FanSwitchLowSpeed"){
						if(tempDiff < settings."${pName}FanSwitchMedSpeed".toDouble()){
							if(speed != "low"){
								sw.setSpeed("low")
								LogAction("doFanOperation: Temp Difference (${tempDiff}${tUnitStr()}) is BELOW the Medium Speed Threshold of (${settings."${pName}FanSwitchMedSpeed"}) | Turning '${sw}' Fan Switch on (LOW SPEED)", "info", false)
								storeLastAction("Set Fan $sw to Low Speed", getDtNow(), pName)
							}
						}
						else if(tempDiff >= settings."${pName}FanSwitchMedSpeed".toDouble() && tempDiff < settings."${pName}FanSwitchHighSpeed".toDouble()){
							if(speed != "medium"){
								sw.setSpeed("medium")
								LogAction("doFanOperation: Temp Difference (${tempDiff}${tUnitStr()}) is ABOVE the Medium Speed Threshold of (${settings."${pName}FanSwitchMedSpeed"}) | Turning '${sw}' Fan Switch on (MEDIUM SPEED)", "info", false)
								storeLastAction("Set Fan $sw to Medium Speed", getDtNow(), pName)
							}
						}
						else if(tempDiff >= settings."${pName}FanSwitchHighSpeed".toDouble()){
							if(speed != "high"){
								sw.setSpeed("high")
								LogAction("doFanOperation: Temp Difference (${tempDiff}${tUnitStr()}) is ABOVE the High Speed Threshold of (${settings."${pName}FanSwitchHighSpeed"}) | Turning '${sw}' Fan Switch on (HIGH SPEED)", "info", false)
								storeLastAction("Set Fan $sw to High Speed", getDtNow(), pName)
							}
						}
					}else{
						if(speed != "high"){
							sw.setSpeed("high")
							LogAction("doFanOperation: Fan supports multiple speeds, with speed control disabled | Turning '${sw}' Fan Switch on (HIGH SPEED)", "info", false)
							storeLastAction("Set Fan $sw to High Speed", getDtNow(), pName)
						}
					}
				}
			}else{
				if(swOn && savedHaveRun && !wantfanOn){
					LogAction("doFanOperation: Fan Switch (${sw?.displayName}) is (${swOn ? "ON" : "OFF"}) | Turning '${sw}' Switch (OFF)", "info", false)
					storeLastAction("Turned Off (${sw})", getDtNow(), pName)
					swOn=false
					sw.off()
					state.haveRunFan=false
				}else{
					if(swOn && !savedHaveRun){
						LogAction("doFanOperation: Saved have run state shows switch ${sw} turned ON outside of automation requests", "info", false)
					}
				}
			}
			if(swOn){ allOff=false }
		}
		if(allOff){ state.haveRunFan=false }
	} catch (ex){
		log.error "doFanOperation Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "doFanOperation", true, getAutoType())
	}
}

Integer getFanCtrlFanRunDtSec(){ return !state.fanCtrlRunDt ? 100000 : GetTimeDiffSeconds(state.fanCtrlRunDt, null, "getFanCtrlFanRunDtSec").toInteger() }
Integer getFanCtrlFanOffDtSec(){ return !state.fanCtrlFanOffDt ? 100000 : GetTimeDiffSeconds(state.fanCtrlFanOffDt, null, "getFanCtrlFanOffDtSec").toInteger() }


// CONTROLS THE THERMOSTAT FAN
def circulateFanControl(operType, Double curSenTemp, Double reqSetpointTemp, Double threshold, can_Circ){
	String pName=fanCtrlPrefix()

	def tstat=settings.schMotTstat
	def tstatsMir=settings.schMotTstatMir

//	input (name: "schMotCirculateTstatFan", type: "bool", title: imgTitle(getAppImg("fan_circulation_icon.png"), inputTitleStr("Run HVAC Fan for Circulation?")), description: desc, required: reqinp, defaultValue: false, submitOnChange: true)
//	input (name: "schMotCirculateExtFan", type: "bool", title: imgTitle(getAppImg("fan_circulation_icon.png"), inputTitleStr("Run External Fan for Circulation?")), description: desc, required: reqinp, defaultValue: false, submitOnChange: true)
//ERS TODO Operate external fan

	Boolean theFanIsOn=false
	String hvacMode=tstat ? tstat?.currentThermostatMode.toString() : null
	String curTstatFanMode=tstat?.currentThermostatFanMode.toString()
	Boolean fanOn=(curTstatFanMode == "on" || curTstatFanMode == "circulate")

	Boolean returnToAuto=can_Circ ? false : true
	if(hvacMode in ["off"]){ returnToAuto=true }

	// Track approximate fan on / off times
	if( !fanOn && state.fanCtrlRunDt > state.fanCtrlFanOffDt ){
		state.fanCtrlFanOffDt=getDtNow()
		returnToAuto=true
	}

	if( fanOn && state.fanCtrlRunDt < state.fanCtrlFanOffDt ){
		state.lastfanCtrlFanRunDt=getDtNow()
	}

	Boolean schedOk=fanCtrlScheduleOk()
	if(!schedOk){
		returnToAuto=true
	}

	String curOperState=tstat?.currentnestThermostatOperatingState.toString()

	Boolean tstatOperStateOk=(curOperState == "idle")
	// if ac or heat is on, we should put fan back to auto
	if(!tstatOperStateOk){
		if( state.fanCtrlFanOffDt > state.fanCtrlRunDt){ return false }
		LogAction("Circulate Fan Run: The Thermostat OperatingState is Currently (${strCapitalize(curOperState)}) Skipping", "info", false)
		state.fanCtrlFanOffDt=getDtNow()
		returnToAuto=true
	}
	Boolean fanTempOk=getCirculateFanTempOk(curSenTemp, reqSetpointTemp, threshold, fanOn, operType)

	if(hvacMode in ["heat", "auto", "cool", "eco"] && fanTempOk && !returnToAuto){
		if(!fanOn){
			Integer waitTimeVal=fanCtrlTimeBetweenRuns?.toInteger() ?: 1200
			Boolean timeSinceLastOffOk=(getFanCtrlFanOffDtSec() > waitTimeVal)
			if(!timeSinceLastOffOk){
				Integer remaining=waitTimeVal - getFanCtrlFanOffDtSec()
				LogAction("Circulate Fan: Want to RUN Fan | Delaying for wait period ${waitTimeVal}, remaining ${remaining} seconds", "info", false)
				Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
				scheduleAutomationEval(val)
				return false // leave off
			}
			LogAction("Circulate Fan: Activating '${tstat?.displayName}'' Fan for ${strCapitalize(operType)}ING Circulation", "info", false)
			tstat?.fanOn()
			storeLastAction("Turned ${tstat} Fan 'On'", getDtNow(), pName, tstat)
			state.fanCtrlRunDt=getDtNow()
			if(tstatsMir){
				tstatsMir?.each { mt ->
					LogAction("Circulate Fan: Mirroring Primary Thermostat: Activating '${mt?.displayName}' Fan", "info", false)
					mt?.fanOn()
					storeLastAction("Turned ${mt.displayName} Fan 'On'", getDtNow(), pName, mt)
				}
			}
		}
		theFanIsOn=true

	}else{
		if(returnToAuto || !fanTempOk){
			if(fanOn && !returnToAuto){
				Integer fanOnTimeVal=fanCtrlOnTime?.toInteger() ?: 240
				Boolean timeSinceLastRunOk=(getFanCtrlFanRunDtSec() > fanOnTimeVal) // fan left on for minimum
				if(!timeSinceLastRunOk){
					def remaining=fanOnTimeVal - getFanCtrlFanRunDtSec()
					LogAction("Circulate Fan Run: Want to STOP Fan | Delaying for run period ${fanOnTimeVal}, remaining ${remaining} seconds", "info", false)
					Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
					scheduleAutomationEval(val)
					return true // leave on
				}
			}
			if(fanOn){
				LogAction("Circulate Fan: Turning OFF '${tstat?.displayName}' Fan that was used for ${strCapitalize(operType)}ING Circulation", "info", false)
				tstat?.fanAuto()
				storeLastAction("Turned ${tstat} Fan to 'Auto'", getDtNow(), pName, tstat)
				state.fanCtrlFanOffDt=getDtNow()
				if(tstatsMir){
					tstatsMir?.each { mt ->
						LogAction("Circulate Fan: Mirroring Primary Thermostat: Turning OFF '${mt?.displayName}' Fan", "info", false)
						mt?.fanAuto()
						storeLastAction("Turned ${mt.displayName} Fan 'Off'", getDtNow(), pName, mt)
					}
				}
			}
		}
		theFanIsOn=false
	}
	if(theFanIsOn){
		scheduleAutomationEval(120)
	}
	return theFanIsOn
}

Boolean getCirculateFanTempOk(Double senTemp, Double reqsetTemp, Double threshold, Boolean fanOn, String operType){

	Boolean turnOn=false
	String tempScaleStr=tUnitStr()
/*
	def adjust=(getTemperatureScale() == "C") ? 0.5 : 1.0
	if(threshold > (adjust * 2.0)){
		adjust=adjust * 2.0
	}

	if(adjust >= threshold){
		LogAction("getCirculateFanTempOk: Bad threshold setting ${threshold} <= ${adjust}", "warn", true)
		return false
	}

	LogAction(" ├ adjust: ${adjust}}${tUnitStr()}", "info", false)
*/

	//LogAction(" ├ operType: (${strCapitalize(operType)}) | Temp Threshold: ${threshold}${tempScaleStr} |  FanAlreadyOn: (${strCapitalize(fanOn)})", "info", false)
	//LogAction(" ├ Sensor Temp: ${senTemp}${tempScaleStr} | Requested Setpoint Temp: ${reqsetTemp}${tempScaleStr}", "info", false)

	if(!reqsetTemp){
		//LogAction("getCirculateFanTempOk: Bad reqsetTemp ${reqsetTemp}", "warn", true)
		//LogAction("getCirculateFanTempOk:", "info", false)
		return false
	}

//	def ontemp
	def offtemp

	if(operType == "cool"){
//		ontemp=reqsetTemp + threshold
		offtemp=reqsetTemp
		if(senTemp >= (offtemp + threshold)){ turnOn=true }
//		if((senTemp > offtemp) && (senTemp <= (ontemp - adjust))){ turnOn=true }
	}
	if(operType == "heat"){
//		ontemp=reqsetTemp - threshold
		offtemp=reqsetTemp
		if(senTemp <= (offtemp - threshold)){ turnOn=true }
//		if((senTemp < offtemp) && (senTemp >= (ontemp + adjust))){ turnOn=true }
	}

//	LogAction(" ├ onTemp: ${ontemp} | offTemp: ${offtemp}}${tempScaleStr}", "info", false)
	//LogAction(" ├ offTemp: ${offtemp}${tempScaleStr} | Temp Threshold: ${threshold}${tempScaleStr}", "info", false)
	//LogAction(" ┌ Final Result: (${strCapitalize(turnOn)})", "info", false)
//	LogAction("getCirculateFanTempOk: ", "info", false)

	String resultStr="getCirculateFanTempOk: The Temperature Difference is "
	if(turnOn){
		resultStr += " within "
	}else{
		resultStr += " Outside "
	}
	Boolean disp=false
	resultStr += "of Threshold Limits | "
	if(!turnOn && fanOn){
		resultStr += "Turning Thermostat Fan OFF"
		disp=true
	}else if(turnOn && !fanOn){
		resultStr += "Turning Thermostat Fan ON"
		disp=true
	}else if(turnOn && fanOn){
		resultStr += "Fan is ON"
	}else if(!turnOn && !fanOn){
		resultStr += "Fan is OFF"
	}
	LogAction(resultStr, "info", disp)

	return turnOn
}


/********************************************************************************
|					HUMIDITY CONTROL AUTOMATION CODE					|
*********************************************************************************/
static String humCtrlPrefix(){ return "humCtrl" }

Boolean isHumCtrlConfigured(){
	return settings.schMotHumidityControl && (settings.humCtrlUseWeather || settings.humCtrlTempSensor) && settings.humCtrlHumidity && settings.humCtrlSwitches
}

String humCtrlSwitchDesc(showOpt=true){
	if(settings.humCtrlSwitches){
		Integer cCnt=settings.humCtrlSwitches.size() ?: 0
		String str=""
		Integer cnt=0
		str += "Switch Status:"
		settings.humCtrlSwitches.sort { it?.displayName }?.each { dev ->
			cnt=cnt+1
			String val=strCapitalize(dev?.currentSwitch) ?: "Not Set"
			str += "${(cnt >= 1) ? "${(cnt == cCnt) ? "\n└" : "\n├"}" : "\n└"} ${dev?.label}: (${val})"
		}

		if(showOpt){
			str += (settings.humCtrlSwitchTriggerType || settings.humCtrlSwitchHvacModeFilter) ? "\n\nSwitch Triggers:" : ""
			str += (settings.humCtrlSwitchTriggerType) ? "\n  • Switch Trigger: (${getEnumValue(switchRunEnum(true), settings.humCtrlSwitchTriggerType)})" : ""
			str += (settings.humCtrlSwitchHvacModeFilter) ? "\n  • Hvac Mode Filter: (${getEnumValue(fanModeTrigEnum(), settings.humCtrlSwitchHvacModeFilter).toString().replaceAll("\\[|\\]", "")})" : ""
		}

		return str
	}
	return null
}

String humCtrlHumidityDesc(){
	if(settings.humCtrlHumidity){
		Integer cCnt=settings.humCtrlHumidity.size() ?: 0
		String str=""
		Integer cnt=0
		str += "Sensor Humidity (average): (${getDeviceVarAvg(settings.humCtrlHumidity, "currentHumidity")}%)"
		settings.humCtrlHumidity.sort { it?.displayName }?.each { dev ->
			cnt=cnt+1
			String t0=strCapitalize(dev?.currentHumidity)
			String val=t0 ?: "Not Set"
			str += "${(cnt >= 1) ? "${(cnt == cCnt) ? "\n└" : "\n├"}" : "\n└"} ${dev?.label}: ${(dev?.label?.toString()?.length() > 10) ? "\n${(cCnt == 1 || cnt == cCnt) ? "    " : "│"}└ " : ""}(${val}%)"
		}
		return str
	}
	return null
}

def getHumCtrlTemperature(){
	def extTemp=0.0
	if(!settings.humCtrlUseWeather && settings.humCtrlTempSensor){
		extTemp=getDeviceTemp(settings.humCtrlTempSensor)
	}else{
		if(settings.humCtrlUseWeather && (state.curWeaTemp_f || state.curWeaTemp_c)){
			if(getTemperatureScale() == "C"){ extTemp=state.curWeaTemp_c.toDouble() }
			else { extTemp=state.curWeaTemp_f.toDouble() }
		}
	}
	return extTemp
}

Integer getMaxHumidity(curExtTemp){
	Integer maxhum=15
	if(curExtTemp != null){
		if(curExtTemp >= adj_temp(40)){
			maxhum=45
		}else if(curExtTemp >= adj_temp(32)){
			maxhum=45 - ( (adj_temp(40) - curExtTemp)/(adj_temp(40)-adj_temp(32)) ) * 5
			//maxhum=40
		}else if(curExtTemp >= adj_temp(20)){
			maxhum=40 - ( (adj_temp(32) - curExtTemp)/(adj_temp(32)-adj_temp(20)) ) * 5
			//maxhum=35
		}else if(curExtTemp >= adj_temp(10)){
			maxhum=35 - ( (adj_temp(20) - curExtTemp)/(adj_temp(20)-adj_temp(10)) ) * 5
			//maxhum=30
		}else if(curExtTemp >= adj_temp(0)){
			maxhum=30 - ( (adj_temp(10) - curExtTemp)/(adj_temp(10)-adj_temp(0)) ) * 5
			//maxhum=25
		}else if(curExtTemp >= adj_temp(-10)){
			maxhum=25 - Math.abs( (adj_temp(0) - curExtTemp) / (adj_temp(0)-adj_temp(-10)) ) * 5
			//maxhum=20
		}else if(curExtTemp >= adj_temp(-20)){
			maxhum=15
		}
	}
	return maxhum
}

Boolean humCtrlScheduleOk(){ return autoScheduleOk(humCtrlPrefix()) }

void humCtrlCheck(){
	//LogAction("humCtrlCheck", "info", false)
	String pName=humCtrlPrefix()
	if(getIsAutomationDisabled()){ return }
	try {
		Long execTime=now()

		def tstat=settings.schMotTstat
		String hvacMode=tstat ? tstat.currentThermostatMode.toString() : null
		String curTstatOperState=tstat.currentThermostatOperatingState.toString()
		String curTstatFanMode=tstat.currentThermostatFanMode.toString()
		//def curHum=humCtrlHumidity?.currentHumidity
		def curHum=getDeviceVarAvg(settings.humCtrlHumidity, "currentHumidity")
		def curExtTemp=getHumCtrlTemperature()
		def maxHum=getMaxHumidity(curExtTemp)
		Boolean schedOk=humCtrlScheduleOk()

		LogAction("humCtrlCheck: ( Humidity: (${curHum}) | External Temp: (${curExtTemp}) | Max Humidity: (${maxHum}) | HvacMode: (${hvacMode}) | OperatingState: (${curTstatOperState}) )", "info", false)

		if(state.haveRunHumidifier == null){ state.haveRunHumidifier=false }
		Boolean savedHaveRun=state.haveRunHumidifier

		Boolean humOn=false

		if(curHum < maxHum){
			humOn=true
		}

//	1:"Heating/Cooling", 2:"With Fan Only", 3:"Heating", 4:"Cooling" 5:"All Operating Modes"

		List validOperModes=[]
		Boolean validOperating=true
		switch ( settings.humCtrlSwitchTriggerType?.toInteger() ){
			case 1:
				validOperModes=["heating", "cooling"]
				validOperating=(curTstatOperState in validOperModes)
				break
			case 2:
				validOperating=(curTstatFanMode in ["on", "circulate"])
				break
			case 3:
				validOperModes=["heating"]
				validOperating=(curTstatOperState in validOperModes)
				break
			case 4:
				validOperModes=["cooling"]
				validOperating=(curTstatOperState in validOperModes)
				break
			case 5:
				break
			default:
				break
		}

		Boolean validHvac=true
		if( !( ("any" in settings.humCtrlSwitchHvacModeFilter) || (hvacMode in settings.humCtrlSwitchHvacModeFilter) ) ){
			//LogAction("humCtrlCheck: Evaluating turn humidifier off; Thermostat Mode does not Match the required Mode", "info", false)
			validHvac=false  // force off
		}

		Boolean turnOn=(humOn && validOperating && validHvac && schedOk) ?: false
		//LogAction("humCtrlCheck: turnOn: ${turnOn} | humOn: ${humOn} | validOperating: ${validOperating} | validHvac: ${validHvac} | schedOk: ${schedOk} | savedHaveRun: ${savedHaveRun}", "info", false)

		settings.humCtrlSwitches?.each { sw ->
			Boolean swOn=(sw?.currentSwitch.toString() == "on")
			if(turnOn){
				//if(!swOn && !savedHaveRun){
				if(!swOn){
					LogAction("humCtrlCheck: Fan Switch (${sw?.displayName}) is (${swOn ? "ON" : "OFF"}) | Turning '${sw}' Switch (ON)", "info", false)
					sw.on()
					swOn=true
					state.haveRunHumidifier=true
					storeLastAction("Turned On $sw)", getDtNow(), pName)
				}else{
					if(!swOn && savedHaveRun){
						LogAction("humCtrlCheck: savedHaveRun state shows switch ${sw} turned OFF outside of automation requests", "info", false)
					}
				}
			}else{
				//if(swOn && savedHaveRun){
				if(swOn){
					LogAction("humCtrlCheck: Fan Switch (${sw?.displayName}) is (${swOn ? "ON" : "OFF"}) | Turning '${sw}' Switch (OFF)", "info", false)
					storeLastAction("Turned Off (${sw})", getDtNow(), pName)
					sw.off()
					state.haveRunHumidifier=false
				}else{
					if(swOn && !savedHaveRun){
						LogAction("humCtrlCheck: Saved have run state shows switch ${sw} turned ON outside of automation requests", "info", false)
					}
					state.haveRunHumidifier=false
				}
			}
		}
		storeExecutionHistory((now()-execTime), "humCtrlCheck")

	} catch (ex){
		log.error "humCtrlCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "humCtrlCheck", true, getAutoType())
	}
	return
}


/********************************************************************************
|					EXTERNAL TEMP AUTOMATION CODE					|
*********************************************************************************/
static String extTmpPrefix(){ return "extTmp" }

Boolean isExtTmpConfigured(){
	return settings.schMotExternalTempOff && (settings.extTmpUseWeather || settings.extTmpTempSensor) && settings.extTmpDiffVal
}

Integer getWeathUpdSec(){ return !state.weatherUpdDt ? 100000 : GetTimeDiffSeconds(state.weatherUpdDt, null, "getWeathUpdSec").toInteger() }

def getExtConditions( doEvent=false ){
	LogTrace("getExtConditions")
	Long execTime=now()
	def t0
	if(state.wDevInst == null){
		state.wDevInst=false
		t0=parent.getSettingVal("weatherDevice")
		state.wDevInst=t0 ? true : false
	}
	if((Boolean)state.wDevInst){
		def weather=parent.getSettingVal("weatherDevice")
		if(weather){
			def temp0
			def hum0
			if(state.needWeathUpd || getWeathUpdSec() > 3600){
				stateRemove("needWeathUpd")
				state.weatherUpdDt=getDtNow()
				try {
					weather.refresh()
				} catch (ex){
					log.error "getExtConditions Exception: ${ex?.message}"
					//parent?.sendExceptionData(ex, "getExtConditions", true, getAutoType())
				}
			}

			temp0=getDeviceTempAvg(weather)
			hum0=getDeviceVarAvg(weather, "currentHumidity")

			if(temp0 || hum0){ state.curWeather=true }
			else { state.curWeather=null; return }
			//Logger("temp0: ${temp0} hum0: ${hum0} loc: ${state.curWeatherLoc}")

			state.curWeatherLoc="${weather?.currentCity} ${weather?.currentCountry}"
			state.curWeatherHum=hum0

			Double c_temp=0.0
			Long f_temp=0
			if(getTemperatureScale() == "C"){
				c_temp=temp0
				f_temp=((c_temp * (9 / 5)) + 32)
			}else{
				f_temp=temp0
				c_temp=((f_temp - 32) * (5 / 9))
			}
			state.curWeaTemp_f=Math.round(f_temp) as Integer
			state.curWeaTemp_c=Math.round(c_temp.round(1) * 2) / 2.0f

			c_temp=estimateDewPoint(hum0, c_temp)
			if(state.curWeaTemp_c < c_temp){ c_temp=state.curWeaTemp_c }
			f_temp=c_temp * 9.0/5.0 + 32.0
			state.curWeatherDewpointTemp_c=Math.round(c_temp.round(1) * 2) / 2.0f
			state.curWeatherDewpointTemp_f=Math.round(f_temp) as Integer
		}
	}
	storeExecutionHistory((now()-execTime), "getExtConditions")
}

private Double estimateDewPoint(double rh,double t){
	double L=Math.log(rh/100)
	double M=17.27 * t
	double N=237.3 + t
	double B=(L + (M/N)) / 17.27
	double dp=(237.3 * B) / (1 - B)

	double dp1=243.04D * ( Math.log(rh / 100) + ( (17.625D * t) / (243.04 + t) ) ) / (17.625 - Math.log(rh / 100) - ( (17.625 * t) / (243.04 + t) ) )
	double ave=(dp + dp1)/2.0D
	//LogAction("dp: ${dp.round(1)} dp1: ${dp1.round(1)} ave: ${ave.round(1)}")
	ave=dp1
	return ave.round(1)
}

def getExtTmpTemperature(){
	def extTemp=0.0
	if(!settings.extTmpUseWeather && settings.extTmpTempSensor){
		extTemp=getDeviceTemp(settings.extTmpTempSensor)
	}else{
		if(settings.extTmpUseWeather && (state.curWeaTemp_f || state.curWeaTemp_c)){
			if(getTemperatureScale() == "C"){ extTemp=state.curWeaTemp_c.toDouble() }
			else { extTemp=state.curWeaTemp_f.toDouble() }
		}
	}
	return extTemp
}

def getExtTmpDewPoint(){
	def extDp=0.0
	if(settings.extTmpUseWeather && (state.curWeatherDewpointTemp_f || state.curWeatherDewpointTemp_c)){
		if(getTemperatureScale() == "C"){ extDp=roundTemp(state.curWeatherDewpointTemp_c.toDouble()) }
		else { extDp=roundTemp(state.curWeatherDewpointTemp_f.toDouble()) }
	}
//TODO if an external sensor, if it has temp and humidity, we can calculate DP
	return extDp
}

def getDesiredTemp(){
	def extTmpTstat=settings.schMotTstat
	String curMode=extTmpTstat ? extTmpTstat.currentThermostatMode?.toString() : null
	Boolean modeOff=(curMode in ["off"])
	Boolean modeEco=(curMode in ["eco"])
	Boolean modeCool=(curMode == "cool")
	Boolean modeHeat=(curMode == "heat")
	Boolean modeAuto=(curMode == "auto")

	def desiredHeatTemp=getRemSenHeatSetTemp(curMode)
	def desiredCoolTemp=getRemSenCoolSetTemp(curMode)
	String lastMode=extTmpTstat?.currentpreviousthermostatMode?.toString()
	if(modeEco){
		if( !lastMode && state.extTmpTstatOffRequested && state.extTmplastMode){
			lastMode=state.extTmplastMode
			//state.extTmpSavedTemp
		}
		if(lastMode){
			desiredHeatTemp=getRemSenHeatSetTemp(lastMode, modeEco, false)
			desiredCoolTemp=getRemSenCoolSetTemp(lastMode, modeEco, false)
			if(!desiredHeatTemp){ desiredHeatTemp=state.extTmpSavedHTemp }
			if(!desiredCoolTemp){ desiredCoolTemp=state.extTmpSavedCTemp }
			//LogAction("getDesiredTemp: Using lastMode: ${lastMode} | extTmpTstatOffRequested: ${state.extTmpTstatOffRequested} | curMode: ${curMode}", "info", false)
			modeOff=(lastMode in ["off"])
			modeCool=(lastMode == "cool")
			modeHeat=(lastMode == "heat")
			modeAuto=(lastMode == "auto")
		}
	}

	def desiredTemp=0
	if(!modeOff){
		if(desiredHeatTemp && modeHeat)		{ desiredTemp=desiredHeatTemp }
		else if(desiredCoolTemp && modeCool)	{ desiredTemp=desiredCoolTemp }
		else if(desiredHeatTemp && desiredCoolTemp && (desiredHeatTemp < desiredCoolTemp) && modeAuto ){
			desiredTemp=(desiredCoolTemp + desiredHeatTemp) / 2.0
		}
		//else if(desiredHeatTemp && modeEco)	{ desiredTemp=desiredHeatTemp }
		//else if(desiredCoolTemp && modeEco)	{ desiredTemp=desiredCoolTemp }
		else if(!desiredTemp && state.extTmpSavedTemp){ desiredTemp=state.extTmpSavedTemp }

		//LogAction("getDesiredTemp: curMode: ${curMode} | lastMode: ${lastMode} | Desired Temp: ${desiredTemp} | Desired Heat Temp: ${desiredHeatTemp} | Desired Cool Temp: ${desiredCoolTemp} extTmpSavedTemp: ${state.extTmpSavedTemp}", "info", false)
	}

	return desiredTemp
}

Boolean extTmpTempOk(disp=false, last=false){
	//LogTrace("extTmpTempOk")
	String pName=extTmpPrefix()
	try {
		Long execTime=now()
		def extTmpTstat=settings.schMotTstat
		def extTmpTstatMir=settings.schMotTstatMir

		def intTemp=extTmpTstat ? getRemoteSenTemp().toDouble() : null
		def extTemp=getExtTmpTemperature()

		def dpLimit=getComfortDewpoint(extTmpTstat)
		def curDp=getExtTmpDewPoint()
		def diffThresh=Math.abs(getExtTmpTempDiffVal())

		String curMode=extTmpTstat ? extTmpTstat?.currentThermostatMode?.toString() : null
		Boolean modeOff=(curMode == "off")
		Boolean modeCool=(curMode == "cool")
		Boolean modeHeat=(curMode == "heat")
		Boolean modeEco=(curMode == "eco")
		Boolean modeAuto=(curMode == "auto")

		Boolean canHeat=state.schMotTstatCanHeat
		Boolean canCool=state.schMotTstatCanCool

		//LogAction("extTmpTempOk: Inside Temp: ${intTemp} | curMode: ${curMode} | modeOff: ${modeOff} | modeEco: ${modeEco} | modeAuto: ${modeAuto} || extTmpTstatOffRequested: ${state.extTmpTstatOffRequested}", "info", false)

		Boolean retval=true
		Boolean externalTempOk=true
		Boolean internalTempOk=true

		Boolean dpOk= curDp<dpLimit || !canCool
		if(!dpOk){ retval=false }

		String str

/*
		Boolean modeEco=(curMode in ["eco"])
		def home=false
		def away=false
		if(extTmpTstat && getTstatPresence(extTmpTstat) == "present"){ home=true }
		else { away=true }
		if(away && modeEco){			// we won't pull system out of ECO mode if we are away
			retval=false
			str="Nest is away AND in ECO mode"
		}
*/

		if(!getSafetyTempsOk(extTmpTstat)){
			retval=false
			externalTempOk=false
			str="within safety Temperatures "
			LogAction("extTmpTempOk: Safety Temps not OK", "warn", true)
		}

		if(modeOff){
			retval=false
		}

		def desiredHeatTemp
		def desiredCoolTemp
		if(modeAuto && retval){
			desiredHeatTemp=getRemSenHeatSetTemp(curMode)
			desiredCoolTemp=getRemSenCoolSetTemp(curMode)
		}

		def lastMode=extTmpTstat?.currentpreviousthermostatMode?.toString()
		if(curMode == "eco"){
			if(!lastMode && state.extTmpTstatOffRequested && state.extTmplastMode){
				lastMode=state.extTmplastMode
				//state.extTmpSavedTemp
			}
			if(lastMode){
				//LogAction("extTmpTempOk: Resetting mode curMode: ${curMode} | to previous mode lastMode: ${lastMode} | extTmpTstatOffRequested: ${state.extTmpTstatOffRequested}", "info", false)
				desiredHeatTemp=getRemSenHeatSetTemp(lastMode, modeEco, false)
				desiredCoolTemp=getRemSenCoolSetTemp(lastMode, modeEco, false)
				if(!desiredHeatTemp){ desiredHeatTemp=state.extTmpSavedHTemp }
				if(!desiredCoolTemp){ desiredCoolTemp=state.extTmpSavedCTemp }
				//modeOff=(lastMode == "off")
				modeCool=(lastMode == "cool")
				modeHeat=(lastMode == "heat")
				modeEco=(lastMode == "eco")
				modeAuto=(lastMode == "auto")
			}
		}

		if(modeAuto && retval && desiredHeatTemp && desiredCoolTemp){
			if( !(extTemp >= (desiredHeatTemp+diffThresh) && extTemp <= (desiredCoolTemp-diffThresh)) ){
				retval=false
				externalTempOk=false
				str="within range (${desiredHeatTemp} ${desiredCoolTemp})"
			}
//ERS
			state.extTmpSavedHTemp=desiredHeatTemp
			state.extTmpSavedCTemp=desiredCoolTemp
		}

		def tempDiff
		def desiredTemp
		def insideThresh

		if(!modeAuto && retval){
			desiredTemp=getDesiredTemp()
//ERS
			if(desiredTemp){ state.extTmpSavedTemp=desiredTemp }
			if(!desiredTemp){
				desiredTemp=intTemp
				if(!modeOff){
					LogAction("extTmpTempOk: No Desired Temp found, using interior Temp", "warn", true)
				}
				retval=false
			}else{
				tempDiff=Math.abs(extTemp - desiredTemp)
				str="enough different (${tempDiff})"
				insideThresh=getExtTmpInsideTempDiffVal()
				LogAction("extTmpTempOk: Outside Temp: ${extTemp} | Inside Temp: ${intTemp} | Desired Temp: ${desiredTemp} | Inside Temp Threshold: ${insideThresh} | Outside Temp Threshold: ${diffThresh} | Actual Difference: ${tempDiff} | Outside Dew point: ${curDp} | Dew point Limit: ${dpLimit}", "info", false)

				if(diffThresh && tempDiff < diffThresh){
					retval=false
					externalTempOk=false
				}
				Boolean extTempHigh= extTemp>=desiredTemp
				Boolean extTempLow= extTemp<=desiredTemp
				String oldMode=state.extTmpRestoreMode
				if(modeCool || oldMode == "cool" || (!canHeat && canCool)){
					str="greater than"
					if(extTempHigh){ retval=false; externalTempOk=false }
					if(intTemp > desiredTemp+insideThresh){ retval=false; internalTempOk=false } // too hot inside
				}
				if(modeHeat || oldMode == "heat" || (!canCool && canHeat)){
					str="less than"
					if(extTempLow){ retval=false; externalTempOk=false }
					if(intTemp < desiredTemp-insideThresh){ retval=false; internalTempOk=false } // too cold inside
				}
				//LogAction("extTmpTempOk: extTempHigh: ${extTempHigh} | extTempLow: ${extTempLow}", "info", false)
			}
		}
		Boolean showRes=disp ? (retval!=last) : false
		if(!dpOk){
			LogAction("extTmpTempOk: ${retval} Dewpoint: (${curDp}${tUnitStr()}) is ${dpOk ? "ok" : "TOO HIGH"}", "info", showRes)
		}else{
			if(!modeAuto){
				LogAction("extTmpTempOk: ${retval} Desired Inside Temp: (${desiredTemp}${tUnitStr()}) is ${externalTempOk ? "" : "Not"} ${str} $diffThresh\u00b0 of Outside Temp: (${extTemp}${tUnitStr()}) ${retval ? "AND" : "OR"} Inside Temp: (${intTemp}) is ${internalTempOk ? "" : "Not"} within Inside Threshold: ${insideThresh} of desired (${desiredTemp})", "info", showRes)
			}else{
				LogAction("extTmpTempOk: ${retval} Exterior Temperature (${extTemp}${tUnitStr()}) is ${externalTempOk ? "" : "Not"} ${str} using $diffThresh\u00b0 offset |  Inside Temp: (${intTemp}${tUnitStr()})", "info", showRes)

			}
		}
		storeExecutionHistory((now() - execTime), "extTmpTempOk")
		return retval
	} catch (ex){
		log.error "extTmpTempOk Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "extTmpTempOk", true, getAutoType())
	}
}

Boolean extTmpScheduleOk(){ return autoScheduleOk(extTmpPrefix()) }
def getExtTmpTempDiffVal(){ return !settings.extTmpDiffVal ? 1.0 : settings.extTmpDiffVal.toDouble() }
def getExtTmpInsideTempDiffVal(){ return !settings.extTmpInsideDiffVal ? (getTemperatureScale() == "C" ? 2 : 4) : settings.extTmpInsideDiffVal.toDouble() }
Integer getExtTmpWhileOnDtSec(){ return !state.extTmpChgWhileOnDt ? 100000 : GetTimeDiffSeconds(state.extTmpChgWhileOnDt, null, "getExtTmpWhileOnDtSec").toInteger() }
Integer getExtTmpWhileOffDtSec(){ return !state.extTmpChgWhileOffDt ? 100000 : GetTimeDiffSeconds(state.extTmpChgWhileOffDt, null, "getExtTmpWhileOffDtSec").toInteger() }

// allow override from schedule?
Integer getExtTmpOffDelayVal(){ return !settings.extTmpOffDelay ? 300 : settings.extTmpOffDelay.toInteger() }
Integer getExtTmpOnDelayVal(){ return !settings.extTmpOnDelay ? 300 : settings.extTmpOnDelay.toInteger() }

void extTmpTempCheck(Boolean cTimeOut=false){
	//LogAction("extTmpTempCheck", "info", false)
	String pName=extTmpPrefix()

	try {
		if(getIsAutomationDisabled()){ return }
		else {

			def extTmpTstat=settings.schMotTstat
			def extTmpTstatMir=settings.schMotTstatMir

			Long execTime=now()
			//state.autoRunDt=getDtNow()

			if(state."${pName}TimeoutOn" == null){ state."${pName}TimeoutOn"=false }
			if(cTimeOut){ state."${pName}TimeoutOn"=true }
			Boolean timeOut=state."${pName}TimeoutOn" ?: false

			String curMode=extTmpTstat ? extTmpTstat?.currentThermostatMode?.toString() : null
			Boolean modeOff=(curMode in ["off"])
			Boolean modeInActive=(curMode in ["off", "eco"])
			Boolean modeEco=(curMode in ["eco"])
			Boolean modeAuto=(curMode == "auto")
			Boolean allowNotif=settings."${pName}NotifOn"
//			def allowSpeech=allowNotif && settings."${pName}AllowSpeechNotif"
			Boolean allowAlarm=allowNotif && settings."${pName}AllowAlarmNotif"
//			def speakOnRestore=allowSpeech && settings."${pName}SpeechOnRestore"

			if(!modeInActive){ state."${pName}TimeoutOn"=false; timeOut=false }
// if we requested off; and someone switched us on or nMode took over...
			if( state.extTmpTstatOffRequested && (!modeEco || (modeEco && parent.setNModeActive(null))) ){  // reset timer and states
				LogAction("extTmpTempCheck: | ${!modeEco ? "HVAC turned on when automation had OFF" : "Automation overridden by nMODE"}, resetting state to match", "warn", true)
				state.extTmpChgWhileOnDt=getDtNow()
				state.extTmpTstatOffRequested=false
				state.extTmpChgWhileOffDt=getDtNow()
				state.extTmpRestoreMode=null
				state."${pName}TimeoutOn"=false
				unschedTimeoutRestore(pName)
			}

			if(modeOff){
				storeExecutionHistory((now() - execTime), "extTmpTempCheck")
				return
			}

			String mylastMode=state.extTmplastMode  // when we state change that could change desired Temp ensure delays happen before off can happen again
			def lastDesired=state.extTmpSavedTemp   // this catches scheduled temp or hvac mode changes
			def desiredTemp=getDesiredTemp()

			if( (mylastMode != curMode) || (desiredTemp && desiredTemp != lastDesired)){
				if(!modeInActive){
					state.extTmplastMode=curMode
//ERS
					if(desiredTemp){ state.extTmpSavedTemp=desiredTemp }
					def desiredHeatTemp
					def desiredCoolTemp
					if(modeAuto){
						desiredHeatTemp=getRemSenHeatSetTemp(curMode)
						desiredCoolTemp=getRemSenCoolSetTemp(curMode)
						if(desiredHeatTemp && desiredCoolTemp){
							state.extTmpSavedHTemp=desiredHeatTemp
							state.extTmpSavedCTemp=desiredCoolTemp
						}
					}
					state.extTmpChgWhileOnDt=getDtNow()
				}else{
					//state.extTmpChgWhileOffDt=getDtNow()
				}
			}

			Boolean safetyOk=getSafetyTempsOk(extTmpTstat)
			Boolean schedOk=extTmpScheduleOk()
			Boolean okToRestore= modeEco && state.extTmpTstatOffRequested && state.extTmpRestoreMode
			Boolean tempWithinThreshold=extTmpTempOk( ((modeEco && okToRestore) || (!modeEco && !okToRestore)), okToRestore)

			if(!tempWithinThreshold || timeOut || !safetyOk || !schedOk){
				if(allowAlarm){ alarmEvtSchedCleanup(extTmpPrefix()) }
				String rmsg=""
				if(okToRestore){
					if(getExtTmpWhileOffDtSec() >= (getExtTmpOnDelayVal() - 5) || timeOut || !safetyOk){
						String lastMode=null
						if(state.extTmpRestoreMode){
							lastMode=extTmpTstat?.currentpreviousthermostatMode?.toString()
							if(!lastMode){ lastMode=state.extTmpRestoreMode }
						}
						if(lastMode && (lastMode != curMode || timeOut || !safetyOk || !schedOk)){
							scheduleAutomationEval(70)
							if(setTstatMode(extTmpTstat, lastMode, pName)){
								storeLastAction("Restored Mode ($lastMode)", getDtNow(), pName, extTmpTstat)
								state.extTmpRestoreMode=null
								state.extTmpTstatOffRequested=false
								state.extTmpRestoredDt=getDtNow()
								state.extTmpChgWhileOnDt=getDtNow()
								state."${pName}TimeoutOn"=false
								unschedTimeoutRestore(pName)

								if(extTmpTstatMir){
									if(setMultipleTstatMode(extTmpTstatMir, lastMode, pName)){
										LogAction("Mirroring (${lastMode}) Restore to ${extTmpTstatMir}", "info", false)
									}
								}

								rmsg="extTmpTempCheck: Restoring '${extTmpTstat?.label}' to '${strCapitalize(lastMode)}' mode: "
								Boolean needAlarm=false
								if(!safetyOk){
									rmsg += "External Temp Safety Temps reached"
									needAlarm=true
								}else if(!schedOk){
									rmsg += "the schedule does not allow automation control"
								}else if(timeOut){
									rmsg += "the (${getEnumValue(longTimeSecEnum(), extTmpOffTimeout)}) Timeout reached"
								}else{
									rmsg += "External Temp above the Threshold for (${getEnumValue(longTimeSecEnum(), extTmpOnDelay)})"
								}
								LogAction(rmsg, (needAlarm ? "warn" : "info"), true)
								if(allowNotif){
									if(!timeOut && safetyOk){
										sendEventPushNotifications(rmsg, "Info", pName)  // this uses parent and honors quiet times others do NOT
//										if(speakOnRestore){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OnVoiceMsg", pName), pName, "nmExtTmpOn_${app?.id}", true, "nmExtTmpOff_${app?.id}") }
									}else if(needAlarm){
										sendEventPushNotifications(rmsg, "Warning", pName)
										if(allowAlarm){ scheduleAlarmOn(pName) }
									}
								}
								storeExecutionHistory((now() - execTime), "extTmpTempCheck")
								return

							}else{ LogAction("extTmpTempCheck: | There was problem restoring the last mode to '", "error", true) }
						}else{
							if(!lastMode){
								LogAction("extTmpTempCheck: | Unable to restore settings: previous mode not found. Likely other automation operation", "warn", true)
								state.extTmpTstatOffRequested=false
							}else if(!timeOut && safetyOk){ LogAction("extTmpTstatCheck: | Skipping Restore: the Mode to Restore is same as Current Mode ${curMode}", "info", false) }
							if(!safetyOk){ LogAction("extTmpTempCheck: | Unable to restore mode and safety temperatures are exceeded", "warn", true) }
							// TODO check if timeout quickly cycles back
						}
					}else{
						if(safetyOk){
							Integer remaining=getExtTmpOnDelayVal() - getExtTmpWhileOffDtSec()
							LogAction("extTmpTempCheck: Delaying restore for wait period ${getExtTmpOnDelayVal()}, remaining ${remaining}", "info", false)
							Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
							scheduleAutomationEval(val)
						}
					}
				}else{
					if(modeInActive){
						if(timeout || !safetyOk){
							LogAction("extTmpTempCheck: | Timeout or Safety temps exceeded and Unable to restore settings okToRestore is false", "warn", true)
							state."${pName}TimeoutOn"=false
						}
						else if( (!state.extTmpRestoreMode && state.extTmpTstatOffRequested) ||
								(state.extTmpRestoreMode && !state.extTmpTstatOffRequested) ){
							LogAction("extTmpTempCheck: | Unable to restore settings: previous mode not found.", "warn", true)
							state.extTmpRestoreMode=null
							state.extTmpTstatOffRequested=false
						}
					}
				}
			}

			if(tempWithinThreshold && !timeOut && safetyOk && schedOk && !modeEco){
				String rmsg=""
				if(!modeInActive){
					if(getExtTmpWhileOnDtSec() >= (getExtTmpOffDelayVal() - 2)){
						state."${pName}TimeoutOn"=false
						state.extTmpRestoreMode=curMode
						LogAction("extTmpTempCheck: Saving ${extTmpTstat?.label} (${strCapitalize(state.extTmpRestoreMode)}) mode", "info", false)
						scheduleAutomationEval(70)
						if(setTstatMode(extTmpTstat, "eco", pName)){
							storeLastAction("Set Thermostat ${extTmpTstat?.displayName} to ECO", getDtNow(), pName, extTmpTstat)
							state.extTmpTstatOffRequested=true
							state.extTmpChgWhileOffDt=getDtNow()
							scheduleTimeoutRestore(pName)
							modeInActive=true
							modeEco=true
							rmsg="${extTmpTstat.label} turned 'ECO': External Temp is at the temp threshold for (${getEnumValue(longTimeSecEnum(), extTmpOffDelay)})"
							if(extTmpTstatMir){
								if(setMultipleTstatMode(extTmpTstatMir, "eco", pName)){
									LogAction("Mirroring (ECO) Mode to ${extTmpTstatMir}", "info", false)
								}
							}
							LogAction(rmsg, "info", false)
							if(allowNotif){
								sendEventPushNotifications(rmsg, "Info", pName) // this uses parent and honors quiet times, others do NOT
//								if(allowSpeech){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OffVoiceMsg",pName), pName, "nmExtTmpOff_${app?.id}", true, "nmExtTmpOn_${app?.id}") }
								if(allowAlarm){ scheduleAlarmOn(pName) }
							}
						}else{ LogAction("extTmpTempCheck: Error turning themostat to Eco", "warn", true) }
					}else{
						Integer remaining=getExtTmpOffDelayVal() - getExtTmpWhileOnDtSec()
						LogAction("extTmpTempCheck: Delaying ECO for wait period ${getExtTmpOffDelayVal()} seconds | Wait time remaining: ${remaining} seconds", "info", false)
						Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
						scheduleAutomationEval(val)
					}
				}else{
					LogAction("extTmpTempCheck: | Skipping: Exterior temperatures in range and '${extTmpTstat?.label}' mode is 'OFF or ECO'", "info", false)
				}
			}else{
				if(timeOut){ LogAction("extTmpTempCheck: Skipping: active timeout", "info", false) }
				else if(!safetyOk){ LogAction("extTmpTempCheck: Skipping: Safety Temps Exceeded", "info", false) }
				else if(!schedOk){ LogAction("extTmpTempCheck: Skipping: Schedule Restrictions", "info", false) }
				//else if(!tempWithinThreshold){ LogAction("extTmpTempCheck: Exterior temperatures not in range", "info", false) }
				//else if(modeEco){ LogAction("extTmpTempCheck: Skipping: in ECO mode extTmpTstatOffRequested: (${state.extTmpTstatOffRequested})", "info", false) }
			}
			storeExecutionHistory((now() - execTime), "extTmpTempCheck")
		}
	} catch (ex){
		log.error "extTmpTempCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "extTmpTempCheck", true, getAutoType())
	}
}

void extTmpGenericEvt(evt){
	Long startTime=now()
	Long eventDelay=startTime - evt.date.getTime()
	String evntN=(String)evt.name
	LogAction(evntN.toUpperCase()+" Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "debug", false)
	storeLastEventData(evt)
	extTmpDpOrTempEvt(envtN)
}

void extTmpDpOrTempEvt(String type){
	if(getIsAutomationDisabled()){ return }
	else {
		//state.needWeathUpd=false
		if(settings.humCtrlUseWeather || settings.extTmpUseWeather){
			state.needWeathUpd=false
			state.weatherUpdDt=getDtNow()
			getExtConditions()
		}
	}
	if(isExtTmpConfigured()){
		def extTmpTstat=settings.schMotTstat
		String curMode=extTmpTstat ? extTmpTstat?.currentThermostatMode?.toString() : null
		Boolean modeOff=(curMode in ["off"])
		if(modeOff){
			//LogAction("${type} | Thermostat is off HVAC mode: ${curMode}", "info", false)
			return
		}

		Boolean lastTempWithinThreshold=state.extTmpWithinThreshold
		Boolean tempWithinThreshold=extTmpTempOk(false,false)
		state.extTmpWithinThreshold=tempWithinThreshold

		if(lastTempWithinThreshold == null || tempWithinThreshold != lastTempWithinThreshold){

			//def extTmpTstat=settings.schMotTstat
			//def curMode=extTmpTstat ? extTmpTstat?.currentThermostatMode?.toString() : null
			Boolean modeActive=!(curMode in ["off", "eco"])
			Integer offVal=getExtTmpOffDelayVal()
			Integer onVal=getExtTmpOnDelayVal()
			def timeVal

			if(modeActive){
				state.extTmpChgWhileOnDt=getDtNow()
				timeVal=["valNum":offVal, "valLabel":getEnumValue(longTimeSecEnum(), offVal)]
			}else{
				state.extTmpChgWhileOffDt=getDtNow()
				timeVal=["valNum":onVal, "valLabel":getEnumValue(longTimeSecEnum(), onVal)]
			}
			Integer val=Math.min( Math.max(timeVal?.valNum,defaultAutomationTime()), 60)
			LogAction(type+" | External Temp Check scheduled for (${timeVal.valLabel}) HVAC mode: ${curMode}", "info", false)
			scheduleAutomationEval(val)
		}
		//else { LogAction("${type}: Skipping no state change | tempWithinThreshold: ${tempWithinThreshold}", "info", false) }
	}else{
		scheduleAutomationEval()
	}
}

/******************************************************************************
|						WATCH CONTACTS AUTOMATION CODE						|
*******************************************************************************/
static String conWatPrefix(){ return "conWat" }

String autoStateDesc(String autotype){
	String str=""
	String t0=state."${autotype}RestoreMode"
	Boolean t1=state."${autotype}TstatOffRequested"
	str += "ECO State:"
	str += "\n • Mode Adjusted: (${t0 != null ? "TRUE" : "FALSE"})"
	str += "\n •   Last Mode: (${t0 ? strCapitalize(t0) : "Not Set"})"
	str += t1 ? "\n •   Last Eco Requested: (${t1})" : ""
	return str != "" ? str : null
}

String conWatContactDesc(){
	if(settings.conWatContacts){
		Integer cCnt=settings.conWatContacts?.size() ?: 0
		String str=""
		Integer cnt=0
		str += "Contact Status:"
		settings.conWatContacts.sort { it.displayName }?.each { dev ->
			cnt=cnt+1
			String t0=strCapitalize(dev?.currentContact)
			String val=t0 ?: "Not Set"
			str += "${(cnt >= 1) ? "${(cnt == cCnt) ? "\n└" : "\n├"}" : "\n└"} ${dev?.label}: (${val})"
		}
		return str
	}
	return null
}

Boolean isConWatConfigured(){
	return settings.schMotContactOff && settings.conWatContacts && settings.conWatOffDelay
}

Boolean getConWatContactsOk(){ return settings.conWatContacts?.currentContact?.contains("open") ? false : true }
//def conWatContactOk(){ return (!settings.conWatContacts) ? false : true }
Boolean conWatScheduleOk(){ return autoScheduleOk(conWatPrefix()) }
Integer getConWatOpenDtSec(){ return !state.conWatOpenDt ? 100000 : GetTimeDiffSeconds(state.conWatOpenDt, null, "getConWatOpenDtSec").toInteger() }
Integer getConWatCloseDtSec(){ return !state.conWatCloseDt ? 100000 : GetTimeDiffSeconds(state.conWatCloseDt, null, "getConWatCloseDtSec").toInteger() }
Integer getConWatRestoreDelayBetweenDtSec(){ return !state.conWatRestoredDt ? 100000 : GetTimeDiffSeconds(state.conWatRestoredDt, null, "getConWatRestoreDelayBetweenDtSec").toInteger() }

// allow override from schedule?
Integer getConWatOffDelayVal(){ return !settings.conWatOffDelay ? 300 : (settings.conWatOffDelay.toInteger()) }
Integer getConWatOnDelayVal(){ return !settings.conWatOnDelay ? 300 : (settings.conWatOnDelay.toInteger()) }
Integer getConWatRestoreDelayBetweenVal(){ return !settings.conWatRestoreDelayBetween ? 600 : settings.conWatRestoreDelayBetween.toInteger() }

void conWatCheck(Boolean cTimeOut=false){
	LogTrace("conWatCheck $cTimeOut")
	//
	// There should be monitoring of actual temps for min and max warnings given on/off automations
	//
	// Should have some check for stuck contacts
	//
	String pName=conWatPrefix()

	def conWatTstat=settings.schMotTstat
	def conWatTstatMir=settings.schMotTstatMir

	try {
		if(getIsAutomationDisabled()){ return }
		else {
			Long execTime=now()
			//state.autoRunDt=getDtNow()

			if(state."${pName}TimeoutOn" == null){ state."${pName}TimeoutOn"=false }
			if(cTimeOut){ state."${pName}TimeoutOn"=true }
			Boolean timeOut=state."${pName}TimeoutOn" ?: false
			String curMode=conWatTstat ? conWatTstat.currentThermostatMode.toString() : null
			Boolean modeEco=(curMode in ["eco"])
			//def curNestPres=getTstatPresence(conWatTstat)
			Boolean modeOff=(curMode in ["off", "eco"])
			Boolean allowNotif=settings."${pName}NotifOn" ? true : false
//			Boolean allowSpeech=allowNotif && settings."${pName}AllowSpeechNotif"
			Boolean allowAlarm=allowNotif && settings."${pName}AllowAlarmNotif"
//			Boolean speakOnRestore=allowSpeech && settings."${pName}SpeechOnRestore"

			//log.debug "curMode: $curMode | modeOff: $modeOff | conWatRestoreOnClose: $conWatRestoreOnClose | lastMode: $lastMode"
			//log.debug "conWatTstatOffRequested: ${state.conWatTstatOffRequested} | getConWatCloseDtSec(): ${getConWatCloseDtSec()}"

			if(!modeEco){ state."${pName}TimeoutOn"=false; timeOut=false }

// if we requested off; and someone switched us on or nMode took over...
			if( state.conWatTstatOffRequested && (!modeEco || (modeEco && parent.setNModeActive(null))) ){  // so reset timer and states
				LogAction("conWatCheck: | ${!modeEco ? "HVAC turned on when automation had OFF" : "Automation overridden by nMODE"}, resetting state to match", "warn", true)
				state.conWatRestoreMode=null
				state.conWatTstatOffRequested=false
				state.conWatOpenDt=getDtNow()
				state."${pName}TimeoutOn"=false
				unschedTimeoutRestore(pName)
			}

			String mylastMode=state.conWatlastMode  // when we state change modes, ensure delays happen before off can happen again
			state.conWatlastMode=curMode
			if(!modeOff && (mylastMode != curMode)){ state.conWatOpenDt=getDtNow() }

			Boolean safetyOk=getSafetyTempsOk(conWatTstat)
			Boolean schedOk=conWatScheduleOk()
			Boolean okToRestore= modeEco && state.conWatTstatOffRequested
			Boolean contactsOk=getConWatContactsOk()

			if(contactsOk || timeOut || !safetyOk || !schedOk){
				if(allowAlarm){ alarmEvtSchedCleanup(conWatPrefix()) }
				String rmsg=""
				if(okToRestore){
					if(getConWatCloseDtSec() >= (getConWatOnDelayVal() - 5) || timeOut || !safetyOk){
						String lastMode=null
						if(state.conWatRestoreMode){
							lastMode=conWatTstat?.currentpreviousthermostatMode?.toString()
							if(!lastMode){ lastMode=state.conWatRestoreMode }
						}
						if(lastMode && (lastMode != curMode || timeOut || !safetyOk || !schedOk)){
							scheduleAutomationEval(70)
							if(setTstatMode(conWatTstat, lastMode, pName)){
								storeLastAction("Restored Mode ($lastMode) to $conWatTstat", getDtNow(), pName, conWatTstat)
								state.conWatRestoreMode=null
								state.conWatTstatOffRequested=false
								state.conWatRestoredDt=getDtNow()
								state.conWatOpenDt=getDtNow()
								state."${pName}TimeoutOn"=false
								unschedTimeoutRestore(pName)
								modeEco=false
								modeOff=false

								if(conWatTstatMir){
									if(setMultipleTstatMode(conWatTstatMir, lastMode, pName)){
										LogAction("Mirroring (${lastMode}) Restore to ${conWatTstatMir}", "info", false)
									}
								}
								rmsg="Restoring '${conWatTstat?.label}' to '${strCapitalize(lastMode)}' mode: "
								Boolean needAlarm=false
								if(!safetyOk){
									rmsg += "Global Safety Values reached"
									needAlarm=true
								}else if(timeOut){
									rmsg += "(${getEnumValue(longTimeSecEnum(), conWatOffTimeout)}) Timeout reached"
								}else if(!schedOk){
									rmsg += "of Schedule restrictions"
								}else{
									rmsg += "ALL contacts 'Closed' for (${getEnumValue(longTimeSecEnum(), conWatOnDelay)})"
								}

								LogAction(rmsg, (needAlarm ? "warn" : "info"), true)
//ERS
								if(allowNotif){
									if(!timeOut && safetyOk){
										sendEventPushNotifications(rmsg, "Info", pName) // this uses parent and honors quiet times, others do NOT
//										if(speakOnRestore){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OnVoiceMsg",pName), pName, "nmConWatOn_${app?.id}", true, "nmConWatOff_${app?.id}") }
									}else if(needAlarm){
										sendEventPushNotifications(rmsg, "Warning", pName)
										if(allowAlarm){ scheduleAlarmOn(pName) }
									}
								}
								storeExecutionHistory((now() - execTime), "conWatCheck")
								return

							}else{ LogAction("conWatCheck: | There was Problem Restoring the Last Mode to ($lastMode)", "error", true) }
						}else{
							if(!lastMode){
								LogAction("conWatCheck: | Unable to restore settings: previous mode not found. Likely other automation operation", "warn", true)
								state.conWatTstatOffRequested=false
							}else if(!timeOut && safetyOk){ LogAction("conWatCheck: | Skipping Restore: the Mode to Restore is same as Current Mode ${curMode}", "info", false) }
							if(!safetyOk){ LogAction("conWatCheck: | Unable to restore mode and safety temperatures are exceeded", "warn", true) }
						}
					}else{
						if(safetyOk){
							Integer remaining=getConWatOnDelayVal() - getConWatCloseDtSec()
							LogAction("conWatCheck: Delaying restore for wait period ${getConWatOnDelayVal()}, remaining ${remaining}", "info", false)
							Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
							scheduleAutomationEval(val)
						}
					}
				}else{
					if(modeOff){
						if(timeOut || !safetyOk){
							LogAction("conWatCheck: | Timeout or Safety temps exceeded and Unable to restore settings okToRestore is false", "warn", true)
							state."${pName}TimeoutOn"=false
						}
						else if(!state.conWatRestoreMode && state.conWatTstatOffRequested){
							LogAction("conWatCheck: | Unable to restore settings: previous mode not found. Likely other automation operation", "warn", true)
							state.conWatTstatOffRequested=false
						}
					}
				}
			}

			if(!contactsOk && safetyOk && !timeOut && schedOk && !modeEco){
				String rmsg=""
				if(!modeOff){
					if((getConWatOpenDtSec() >= (getConWatOffDelayVal() - 2)) && (getConWatRestoreDelayBetweenDtSec() >= (getConWatRestoreDelayBetweenVal() - 2))){
						state."${pName}TimeoutOn"=false
						state.conWatRestoreMode=curMode
						def t0=getOpenContacts(conWatContacts)
						String openCtDesc=t0 ? " '${t0?.join(", ")}' " : " a selected contact "
						LogAction("conWatCheck: Saving ${conWatTstat?.label} mode (${strCapitalize(state.conWatRestoreMode)})", "info", false)
						LogAction("conWatCheck: ${openCtDesc}${t0?.size() > 1 ? "are" : "is"} still Open: Turning 'OFF' '${conWatTstat?.label}'", "debug", false)
						scheduleAutomationEval(70)
						if(setTstatMode(conWatTstat, "eco", pName)){
							storeLastAction("Set $conWatTstat to 'ECO'", getDtNow(), pName, conWatTstat)
							state.conWatTstatOffRequested=true
							state.conWatCloseDt=getDtNow()
							scheduleTimeoutRestore(pName)
							if(conWatTstatMir){
								if(setMultipleTstatMode(conWatTstatMir, "eco", pName)){
									LogAction("Mirroring (ECO) Mode to ${conWatTstatMir}", "info", false)
								}
							}
							rmsg="${conWatTstat.label} turned to 'ECO': ${openCtDesc}Opened for (${getEnumValue(longTimeSecEnum(), conWatOffDelay)})"
							LogAction(rmsg, "info", false)
							if(allowNotif){
								sendEventPushNotifications(rmsg, "Info", pName) // this uses parent and honors quiet times, others do NOT
//								if(allowSpeech){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OffVoiceMsg",pName), pName, "nmConWatOff_${app?.id}", true, "nmConWatOn_${app?.id}") }
								if(allowAlarm){ scheduleAlarmOn(pName) }
							}
						}else{ LogAction("conWatCheck: Error turning themostat to ECO", "warn", true) }
					}else{
						if(getConWatRestoreDelayBetweenDtSec() < (getConWatRestoreDelayBetweenVal() - 2)){
							Integer remaining=getConWatRestoreDelayBetweenVal() - getConWatRestoreDelayBetweenDtSec()
							//LogAction("conWatCheck: | Skipping ECO change: delay since last restore not met (${getEnumValue(longTimeSecEnum(), conWatRestoreDelayBetween)})", "info", false)
							Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
							scheduleAutomationEval(val)
						}else{
							Integer remaining=getConWatOffDelayVal() - getConWatOpenDtSec()
							LogAction("conWatCheck: Delaying ECO for wait period ${getConWatOffDelayVal()} seconds | Wait time remaining: ${remaining} seconds", "info", false)
							Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
							scheduleAutomationEval(val)
						}
					}
				}else{
					LogAction("conWatCheck: | Skipping ECO change: '${conWatTstat?.label}' mode is '${curMode}'", "info", false)
				}
			}else{
				if(timeOut){ LogAction("conWatCheck: Skipping: active timeout", "info", false) }
				else if(!schedOk){ LogAction("conWatCheck: Skipping: Schedule Restrictions", "info", false) }
				else if(!safetyOk){ LogAction("conWatCheck: Skipping: Safety Temps Exceeded", "warn", true) }
				else if(contactsOk){ LogAction("conWatCheck: Contacts are closed", "info", false) }
				//else if(modeEco){ LogAction("conWatTempCheck: Skipping: in ECO mode conWatTstatOffRequested: (${state.conWatTstatOffRequested})", "info", false) }
			}
			storeExecutionHistory((now() - execTime), "conWatCheck")
		}
	} catch (ex){
		log.error "conWatCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "conWatCheck", true, getAutoType())
	}
}

void conWatContactEvt(evt){
	Long startTime=now()
	Long eventDelay=startTime - (Long)evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "debug", false)
	if(getIsAutomationDisabled()){ return }
	else {
		def conWatTstat=settings.schMotTstat
		String curMode=conWatTstat ? conWatTstat?.currentThermostatMode?.toString() : null
		Boolean isModeOff=(curMode in ["eco"])
		Boolean conOpen=(evt?.value == "open")
		Boolean canSched=false
		def timeVal
		if(conOpen){
			state.conWatOpenDt=getDtNow()
			timeVal=["valNum":getConWatOffDelayVal(), "valLabel":getEnumValue(longTimeSecEnum(), getConWatOffDelayVal())]
			canSched=true
		}
		else if(!conOpen && getConWatContactsOk()){
			state.conWatCloseDt=getDtNow()
			if(isModeOff){
				timeVal=["valNum":getConWatOnDelayVal(), "valLabel":getEnumValue(longTimeSecEnum(), getConWatOnDelayVal())]
				canSched=true
			}
		}
		storeLastEventData(evt)
		if(canSched){
			//LogAction("conWatContactEvt: Contact Check scheduled for (${timeVal?.valLabel})", "info", false)
			Integer val=Math.min( Math.max(timeVal?.valNum,defaultAutomationTime()), 60)
			scheduleAutomationEval(val)
		}else{
			LogAction("conWatContactEvt: Skipping Event", "info", false)
		}
	}
}

/******************************************************************************
|					WATCH FOR LEAKS AUTOMATION LOGIC CODE					|
******************************************************************************/
static String leakWatPrefix(){ return "leakWat" }

String leakWatSensorsDesc(){
	if(settings.leakWatSensors){
		Integer cCnt=settings.leakWatSensors?.size() ?: 0
		String str=""
		Integer cnt=0
		str += "Leak Sensors:"
		settings.leakWatSensors?.sort { it?.displayName }?.each { dev ->
			cnt=cnt+1
			String t0=strCapitalize(dev?.currentWater)
			String val=t0 ?: "Not Set"
			str += "${(cnt >= 1) ? "${(cnt == cCnt) ? "\n└" : "\n├"}" : "\n└"} ${dev?.label}: (${val})"
		}
		return str
	}
	return null
}

Boolean isLeakWatConfigured(){
	return settings.schMotWaterOff && settings.leakWatSensors
}

Boolean getLeakWatSensorsOk(){ return settings.leakWatSensors?.currentWater?.contains("wet") ? false : true }
//def leakWatSensorsOk(){ return (!settings.leakWatSensors) ? false : true }
//def leakWatScheduleOk(){ return autoScheduleOk(leakWatPrefix()) }

// allow override from schedule?
Integer getLeakWatOnDelayVal(){ return !settings.leakWatOnDelay ? 300 : settings.leakWatOnDelay.toInteger() }
Integer getLeakWatDryDtSec(){ return !state.leakWatDryDt ? 100000 : GetTimeDiffSeconds(state.leakWatDryDt, null, "getLeakWatDryDtSec").toInteger() }

void leakWatCheck(){
	//LogTrace("leakWatCheck")
//
//    if we cannot save/restore settings, don't bother turning things off
//
	String pName=leakWatPrefix()
	try {
		if(getIsAutomationDisabled()){ return }
		else {
			def leakWatTstat=settings.schMotTstat
			def leakWatTstatMir=settings.schMotTstatMir

			Long execTime=now()
			//state.autoRunDt=getDtNow()

			String curMode=leakWatTstat?.currentThermostatMode.toString()
			//def curNestPres=getTstatPresence(leakWatTstat)
			Boolean modeOff=(curMode == "off")
			Boolean allowNotif=!!(settings."${pName}NotifOn")
//			Boolean allowSpeech=allowNotif && settings."${pName}AllowSpeechNotif"
			Boolean allowAlarm=allowNotif && settings."${pName}AllowAlarmNotif"
//			Boolean speakOnRestore=allowSpeech && settings."${pName}SpeechOnRestore"

			if(!modeOff && state.leakWatTstatOffRequested){  // someone switched us on when we had turned things off, so reset timer and states
				LogAction("leakWatCheck: | System turned on when automation had OFF, resetting state to match", "warn", true)
				state.leakWatRestoreMode=null
				state.leakWatTstatOffRequested=false
			}

			Boolean safetyOk=getSafetyTempsOk(leakWatTstat)
			//def schedOk=leakWatScheduleOk()
			Boolean okToRestore=(modeOff && state.leakWatTstatOffRequested)
			Boolean sensorsOk=getLeakWatSensorsOk()

			if(sensorsOk || !safetyOk){
				if(allowAlarm){ alarmEvtSchedCleanup(leakWatPrefix()) }
				String rmsg=""

				if(okToRestore){
					if(getLeakWatDryDtSec() >= (getLeakWatOnDelayVal() - 5) || !safetyOk){
						String lastMode=null
						if(state.leakWatRestoreMode){ lastMode=state.leakWatRestoreMode }
						if(lastMode && (lastMode != curMode || !safetyOk)){
							scheduleAutomationEval(70)
							if(setTstatMode(leakWatTstat, lastMode, pName)){
								storeLastAction("Restored Mode ($lastMode) to $leakWatTstat", getDtNow(), pName, leakWatTstat)
								state.leakWatTstatOffRequested=false
								state.leakWatRestoreMode=null
								state.leakWatRestoredDt=getDtNow()

								if(leakWatTstatMir){
									if(setMultipleTstatMode(leakWatTstatMir, lastmode, pName)){
										LogAction("leakWatCheck: Mirroring Restoring Mode (${lastMode}) to ${leakWatTstatMir}", "info", false)
									}
								}
								rmsg="Restoring '${leakWatTstat?.label}' to '${strCapitalize(lastMode)}' mode: "
								Boolean needAlarm=false
								if(!safetyOk){
									rmsg += "External Temp Safety Temps reached"
									needAlarm=true
								}else{
									rmsg += "ALL leak sensors 'Dry' for (${getEnumValue(longTimeSecEnum(), leakWatOnDelay)})"
								}

								LogAction(rmsg, needAlarm ? "warn" : "info", true)
								if(allowNotif){
									if(safetyOk){
										sendEventPushNotifications(rmsg, "Info", pName) // this uses parent and honors quiet times, others do NOT
//										if(speakOnRestore){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OnVoiceMsg", pName), pName, "nmLeakWatOn_${app?.id}", true, "nmLeakWatOff_${app?.id}") }
									}else if(needAlarm){
										sendEventPushNotifications(rmsg, "Warning", pName)
										if(allowAlarm){ scheduleAlarmOn(pName) }
									}
								}
								storeExecutionHistory((now() - execTime), "leakWatCheck")
								return

							}else{ LogAction("leakWatCheck: | There was problem restoring the last mode to ${lastMode}", "error", true) }
						}else{
							if(!safetyOk){
								LogAction("leakWatCheck: | Unable to restore mode and safety temperatures are exceeded", "warn", true)
							}else{
								LogAction("leakWatCheck: | Skipping Restore: Mode to Restore (${lastMode}) is same as Current Mode ${curMode}", "info", false)
							}
						}
					}else{
						if(safetyOk){
							Integer remaining=getLeakWatOnDelayVal() - getLeakWatDryDtSec()
							LogAction("leakWatCheck: Delaying restore for wait period ${getLeakWatOnDelayVal()}, remaining ${remaining}", "info", false)
							Integer val=Math.min( Math.max(remaining,defaultAutomationTime()), 60)
							scheduleAutomationEval(val)
						}
					}
				}else{
					if(modeOff){
						if(!safetyOk){
							LogAction("leakWatCheck: | Safety temps exceeded and Unable to restore settings okToRestore is false", "warn", true)
						}
						else if(!state.leakWatRestoreMode && state.leakWatTstatOffRequested){
							LogAction("leakWatCheck: | Unable to restore settings: previous mode not found. Likely other automation operation", "warn", true)
							state.leakWatTstatOffRequested=false
						}
					}
				}
			}

// tough decision here:  there is a leak, do we care about schedule ?
//		if(!getLeakWatSensorsOk() && safetyOk && schedOk){
			if(!sensorsOk && safetyOk){
				String rmsg=""
				if(!modeOff){
					state.leakWatRestoreMode=curMode
					def t0=getWetWaterSensors(leakWatSensors)
					String wetCtDesc=t0 ? " '${t0?.join(", ")}' " : " a selected leak sensor "
					LogAction("leakWatCheck: Saving ${leakWatTstat?.label} mode (${strCapitalize(state.leakWatRestoreMode)})", "info", false)
					LogAction("leakWatCheck: ${wetCtDesc}${t0?.size() > 1 ? "are" : "is"} Wet: Turning 'OFF' '${leakWatTstat?.label}'", "debug", false)
					scheduleAutomationEval(70)
					if(setTstatMode(leakWatTstat, "off", pName)){
						storeLastAction("Turned Off $leakWatTstat", getDtNow(), pName, leakWatTstat)
						state.leakWatTstatOffRequested=true
						state.leakWatDryDt=null // getDtNow()

						if(leakWatTstatMir){
							if(setMultipleTstatMode(leakWatTstatMir, "off", pName)){
								LogAction("leakWatCheck: Mirroring (Off) Mode to ${leakWatTstatMir}", "info", false)
							}
						}
						rmsg="${leakWatTstat.label} turned 'OFF': ${wetCtDesc}has reported it's WET"
						LogAction(rmsg, "warn", true)
						if(allowNotif){
							sendEventPushNotifications(rmsg, "Warning", pName) // this uses parent and honors quiet times, others do NOT
//							if(allowSpeech){ sendEventVoiceNotifications(voiceNotifString(state."${pName}OffVoiceMsg",pName), pName, "nmLeakWatOff_${app?.id}", true, "nmLeakWatOn_${app?.id}") }
							if(allowAlarm){ scheduleAlarmOn(pName) }
						}
					}else{ LogAction("leakWatCheck: Error turning themostat Off", "warn", true) }
				}else{
					LogAction("leakWatCheck: | Skipping change: '${leakWatTstat?.label}' mode is already 'OFF'", "info", false)
				}
			}else{
				//if(!schedOk){ LogAction("leakWatCheck: Skipping: Schedule Restrictions", "warn", true) }
				if(!safetyOk){ LogAction("leakWatCheck: Skipping: Safety Temps Exceeded", "warn", true) }
				if(sensorsOk){ LogAction("leakWatCheck: Sensors are ok", "info", false) }
			}
			storeExecutionHistory((now() - execTime), "leakWatCheck")
		}
	} catch (ex){
		log.error "leakWatCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "leakWatCheck", true, getAutoType())
	}
}

void leakWatSensorEvt(evt){
	Long startTime=now()
	Long eventDelay=startTime - evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "debug", false)
	if(getIsAutomationDisabled()){ return }
	else {
		String curMode=leakWatTstat?.currentThermostatMode.toString()
		Boolean isModeOff=(curMode == "off") ? true : false
		Boolean leakWet=(evt?.value == "wet") ? true : false

		Boolean canSched=false
		def timeVal
		if(leakWet){
			canSched=true
		}
		else if(!leakWet && getLeakWatSensorsOk()){
			if(isModeOff){
				state.leakWatDryDt=getDtNow()
				timeVal=["valNum":getLeakWatOnDelayVal(), "valLabel":getEnumValue(longTimeSecEnum(), getLeakWatOnDelayVal())]
				canSched=true
			}
		}

		storeLastEventData(evt)
		if(canSched){
			LogAction("leakWatSensorEvt: Leak Check scheduled (${timeVal?.valLabel})", "info", false)
			Integer val=Math.min( Math.max(timeVal?.valNum,defaultAutomationTime()), 60)
			scheduleAutomationEval(val)
		}else{
			LogAction("leakWatSensorEvt: Skipping Event", "info", false)
		}
	}
}

/********************************************************************************
|					MODE AUTOMATION CODE							|
*********************************************************************************/
static String nModePrefix(){ return "nMode" }

def nestModePresPage(){
		//Logger("in nestModePresPage")
	String pName=nModePrefix()
	dynamicPage(name: "nestModePresPage", title: "Nest Mode - Nest Home/Away Automation", uninstall: false, install: true){
		if(!nModePresSensor && !nModeSwitch){
			Boolean modeReq=(nModeHomeModes && nModeAwayModes)
			section(sectionTitleStr("Set Nest Presence with location Modes:")){
				input "nModeHomeModes", "mode", title: imgTitle(getAppImg("mode_home_icon.png"), inputTitleStr("Modes to Set Nest Location 'Home'")), multiple: true, submitOnChange: true, required: modeReq
				if(checkModeDuplication(nModeHomeModes, nModeAwayModes)){
					paragraph imgTitle(getAppImg("i_err"), paraTitleStr("ERROR:\nDuplicate Mode(s) were found under both the Home and Away Modes.\nPlease Correct to Proceed")), required: true, state: null
				}
				input "nModeAwayModes", "mode", title: imgTitle(getAppImg("mode_away_icon.png"), inputTitleStr("Modes to Set Nest Location 'Away'")), multiple: true, submitOnChange: true, required: modeReq
				if(nModeHomeModes || nModeAwayModes){
					//Logger("in part 11")
					String str=""
					String locPres=getNestLocPres()
					String locMode=location.mode.toString()
					str += locMode || locPres ? "Location Mode Status:" : ""
					str += locMode ? "\n${locPres ? "├" : "└"} Hub: (${locMode})" : ""
					str += locPres ? "\n└ Nest Location: (${locPres == "away" ? "Away" : "Home"})" : ""
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("${str}")), state: (str != "" ? "complete" : null)
				}
			}
		}
		if(!nModeHomeModes && !nModeAwayModes && !nModeSwitch){
			section(sectionTitleStr("(Optional) Set Nest Presence using Presence Sensor:")){
				paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("Choose a Presence Sensor(s) to use to set your Nest to Home/Away"))
				String t0=nModePresenceDesc()
				String presDesc=t0 ? "\n\n${t0}" + descriptions("d_ttm") : descriptions("d_ttc")
					//Logger("in part 12")
				input "nModePresSensor", "capability.presenceSensor", title: imgTitle(getAppImg("presence_icon.png"), inputTitleStr("Select Presence Sensor(s)")), description: presDesc, multiple: true, submitOnChange: true, required: false
				if(nModePresSensor){
					if(nModePresSensor.size() > 1){
						paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("How this Works!"))
						paragraph sectionTitleStr("Nest Location will be set to 'Away' when all Presence sensors leave and will return to 'Home' when someone arrives")
					}
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("${t0}")), state: "complete"
				}
			}
		}
		if(!nModePresSensor && !nModeHomeModes && !nModeAwayModes){
					//Logger("in part 13")
			section(sectionTitleStr("(Optional) Set Nest Presence based on the state of a Switch:")){
				input "nModeSwitch", "capability.switch", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Select a Switch")), required: false, multiple: false, submitOnChange: true
				if(nModeSwitch){
					input "nModeSwitchOpt", "enum", title: imgTitle(getAppImg("i_set"), inputTitleStr("Switch State to Trigger 'Away'?")), required: true, defaultValue: "On", options: ["On", "Off"], submitOnChange: true
				}
			}
		}
		if(parent.getSettingVal("cameras")){
			section(sectionTitleStr("Nest Cam Options:")){
				input (name: "nModeCamOnAway", type: "bool", title: imgTitle(getAppImg("camera_green_icon.png"), inputTitleStr("Turn On Nest Cams when Away?")), required: false, defaultValue: false, submitOnChange: true)
				input (name: "nModeCamOffHome", type: "bool", title: imgTitle(getAppImg("camera_gray_icon.png"), inputTitleStr("Turn Off Nest Cams when Home?")), required: false, defaultValue: false, submitOnChange: true)
				if(settings.nModeCamOffHome || settings.nModeCamOnAway){
					paragraph paraTitleStr("Optional")
					paragraph sectionTitleStr("You can choose which cameras are changed when Home/Away.  If you don't select any devices all will be changed.")
					input (name: "nModeCamsSel", type: "capability.soundSensor", title: imgTitle(getAppImg("camera_blue_icon.png"), inputTitleStr("Select your Nest Cams?")), required: false, multiple: true, submitOnChange: true)
				}
			}
		}
		if((nModeHomeModes && nModeAwayModes) || nModePresSensor || nModeSwitch){
			section(sectionTitleStr("Additional Settings:")){
					//Logger("in part 14")
				input (name: "nModeSetEco", type: "bool", title: imgTitle(getDevImg("eco_icon.png"), inputTitleStr("Set ECO mode when away?")), required: false, defaultValue: false, submitOnChange: true)
				input (name: "nModeDelay", type: "bool", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Delay Changes?")), required: false, defaultValue: false, submitOnChange: true)
				if(settings.nModeDelay){
					input "nModeDelayVal", "enum", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Delay before change?")), required: false, defaultValue: 60, options:longTimeSecEnum(), submitOnChange: true
				}
			}
		}
		if(((nModeHomeModes && nModeAwayModes) && !nModePresSensor) || nModePresSensor){
					//Logger("in part 15")
			section(getDmtSectionDesc(pName)){
				String pageDesc=getDayModeTimeDesc(pName)
				href "setDayModeTimePage1", title: imgTitle(getAppImg("i_calf"),inputTitleStr(titles("t_cr"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName": "${pName}"]
			}
			section(sectionTitleStr(titles("t_nt"))){
				String t0=getNotifConfigDesc(pName)
				String pageDesc=t0 ? "${t0}" + descriptions("d_ttm") : ""
				href "setNotificationPage2", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName":"${pName}", "allowSpeech":false, "allowAlarm":false, "showSchedule":true]
			}
		}
/*
		if(state.showHelp){
			section("Help:"){
				href url:"${getAutoHelpPageUrl()}", style:"embedded", required:false, title:"Help and Instructions", description:"", image: getAppImg("info.png")
			}
		}
*/
	}
}

String nModePresenceDesc(){
	if(settings.nModePresSensor){
		Integer cCnt=settings.nModePresSensor.size() ?: 0
		String str=""
		Integer cnt=0
		str += "Presence Status:"
		settings.nModePresSensor.sort { it?.displayName }?.each { dev ->
			cnt=cnt+1
			String t0=strCapitalize(dev?.currentPresence)
			String presState=t0 ?: "No State"
			str += "${(cnt >= 1) ? "${(cnt == cCnt) ? "\n└" : "\n├"}" : "\n└"} ${dev?.label}: ${(dev?.label?.toString()?.length() > 10) ? "\n${(cCnt == 1 || cnt == cCnt) ? "    " : " │"} └ " : ""}(${presState})"
		}
		return str
	}
	return null
}

Boolean isNestModesConfigured(){
	return ((!settings.nModePresSensor && !settings.nModeSwitch && (settings.nModeHomeModes && settings.nModeAwayModes)) || (settings.nModePresSensor && !settings.nModeSwitch) || (!settings.nModePresSensor && settings.nModeSwitch))
}

void nModeGenericEvt(evt){
	Long startTime=now()
	Long eventDelay=startTime - evt.date.getTime()
	LogAction("${evt?.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms", "debug", false)
	if(getIsAutomationDisabled()){ return }
	storeLastEventData(evt)
	if(settings.nModeDelay){
		Integer delay=settings.nModeDelayVal.toInteger() ?: 60
		if(delay > defaultAutomationTime()){
			LogAction("Event | A Check is scheduled (${getEnumValue(longTimeSecEnum(), settings.nModeDelayVal)})", "info", false)
			scheduleAutomationEval(delay)
		}else{ scheduleAutomationEval() }
	}else{
		scheduleAutomationEval()
	}
}

void adjustCameras(Boolean on, String sendAutoType=null){
	def cams=parent.getSettingVal("cameras")
	if(cams){
		def foundCams
		if(settings.nModeCamsSel){
			foundCams=settings.nModeCamsSel
		}else{
			foundCams=cams.collect { parent.getDevice(it) }  //parent.getCameraDevice(it) }
		}
		foundCams.each { dev ->
			if(dev){
				String didstr="On"
				try {
					if(on){
						dev?.on()
					}else{
						dev?.off()
						didstr="Off"
					}
					LogAction("adjustCameras: Turning Streaming ${didstr} for (${dev?.displayName})", "info", false)
					storeLastAction("Turned ${didstr} Streaming ${dev?.displayName}", getDtNow(), sendAutoType)
				}
				catch (ex){
					log.error "adjustCameras() Exception: ${dev?.label} does not support commands on / off ${ex?.message}"
					sendEventPushNotifications("Camera commands not found, check IDE logs and installation instructions", "Warning", nModePrefix())
					//parent?.sendExceptionData(ex, "adjustCameras", true, getAutoType())
				}
				return dev
			}
		}
	}
}

void adjustEco(Boolean on, String senderAutoType){
	def tstats=parent.getSettingVal("thermostats")
	def foundTstats
	if(tstats){
		foundTstats=tstats.collect { dni ->
		//foundTstats=tstats?.each { d1 ->
			def d1=parent.getDevice(dni)
			//def d1=parent.getThermostatDevice(dni)
			if(d1){
				String didstr=null
				String tstatAction=null
				String curMode=d1.currentThermostatMode
				String prevMode=d1.currentpreviousthermostatMode
				//LogAction("adjustEco: CURMODE: ${curMode} ON: ${on} PREVMODE: ${prevMode}", "info", false)

				if(on && !(curMode in ["eco", "off"])){
					didstr="ECO"
					tstatAction="eco"
				}
				if(!on && curMode in ["eco"]){
					if(prevMode && prevMode != curMode){
						didstr=prevMode
						tstatAction=prevMode
					}
				}
				if(didstr){
					Boolean a=setTstatMode(d1, tstatAction, senderAutoType)
					LogAction("adjustEco($on): | Thermostat: ${d1?.displayName} setting to HVAC mode $didstr was $curMode", "debug", false)
					storeLastAction("Set ${d1?.displayName} to $didstr", getDtNow(), senderAutoType, d1)
				}else{
/*
					if(on && (curMode in ["eco"])){ // override device to know nMODE is active
						if(senderAutoType){ sendEcoActionDescToDevice(d1, senderAutoType) } // THIS ONLY WORKS ON NEST THERMOSTATS
					}
*/
					LogAction("adjustEco: | Thermostat: ${d1?.displayName} NOCHANGES CURMODE: ${curMode} ON: ${on} PREVMODE: ${prevMode}", "debug", false)
				}
				return d1
			}else{ LogAction("adjustEco NO D1", "warn", true) }
		}
	}
}

void setAway(Boolean away){
	def tstats=parent.getSettingVal("thermostats")
	String didstr=away ? "AWAY" : "HOME"
	def foundTstats
	if(tstats){
		foundTstats=tstats?.collect { dni ->
		//foundTstats=tstats?.each { d1 ->
			def d1=parent.getDevice(dni)
			//def d1=parent.getThermostatDevice(dni)
			if(d1){
				if(away){
					d1?.away()
				}else{
					d1?.present()
				}
				LogAction("setAway($away): | Thermostat: ${d1?.displayName} setting to $didstr", "debug", false)
				storeLastAction("Set ${d1?.displayName} to $didstr", getDtNow(), "nMode", d1)
				return d1
			}else{ LogAction("setaway NO D1", "warn", true) }
		}
	}else{
		if(away){
			parent.setStructureAway(null, true)
		}else{
			parent.setStructureAway(null, false)
		}
		LogAction("setAway($away): | Setting structure to $didstr", "debug", false)
		storeLastAction("Set structure to $didstr", getDtNow(), "nMode")
	}
}

Boolean nModeScheduleOk(){ return autoScheduleOk(nModePrefix()) }
Integer getnModeActionSec(){ return !state.nModeActionDt ? 100000 : GetTimeDiffSeconds(state.nModeActionDt, null, "getnModeActionSec").toInteger() }

void checkNestMode(){
	LogAction("checkNestMode", "debug", false)
//
// This automation only works with Nest as it toggles non-ST standard home/away
//
	String pName=nModePrefix()
	try {
		if(getIsAutomationDisabled()){ return }
		else if(!nModeScheduleOk()){
			LogAction("checkNestMode: Skipping: Schedule Restrictions", "info", false)
		}else{
			Long execTime=now()
			state.autoRunDt=getDtNow()

			String curStMode=location.mode.toString()
			Boolean allowNotif=!!(settings."${nModePrefix()}NotifOn")
			Boolean nestModeAway=(getNestLocPres() == "home") ? false : true
			String awayPresDesc=(nModePresSensor && !nModeSwitch) ? "All Presence device(s) have left setting " : ""
			String homePresDesc=(nModePresSensor && !nModeSwitch) ? "A Presence Device is Now Present setting " : ""
			String awaySwitDesc=(nModeSwitch && !nModePresSensor) ? "${nModeSwitch} State is 'Away' setting " : ""
			String homeSwitDesc=(nModeSwitch && !nModePresSensor) ? "${nModeSwitch} State is 'Home' setting " : ""
			String modeDesc=((!nModeSwitch && !nModePresSensor) && nModeHomeModes && nModeAwayModes) ? "The ST Mode (${curStMode}) has triggered" : ""
			String awayDesc=awayPresDesc+awaySwitDesc+modeDesc
			String homeDesc=homePresDesc+homeSwitDesc+modeDesc

			Boolean away=false
			Boolean home=false

// ERS figure out what state we are in
			if(nModePresSensor && !nModeSwitch){
				if(!isPresenceHome(nModePresSensor)){
					away=true
				}else{
					home=true
				}
			}else if(nModeSwitch && !nModePresSensor){
				Boolean swOptAwayOn=(nModeSwitchOpt == "On") ? true : false
				if(swOptAwayOn){
					!isSwitchOn(nModeSwitch) ? (home=true) : (away=true)
				}else{
					!isSwitchOn(nModeSwitch) ? (away=true) : (home=true)
				}
			}else if(nModeHomeModes && nModeAwayModes){
				if(isInMode(nModeHomeModes)){
					home=true
				}else{
					if(isInMode(nModeAwayModes)){ away=true }
				}
			}else{
				LogAction("checkNestMode: Nothing Matched", "info", true)
			}

// Track changes that happen outside of nMode
// this won't attempt to reset Nest device eco or camera state - you chose to do it outside the automation
			Boolean NMisEnabled=parent.automationNestModeEnabled(true)
			Boolean NMecoisEnabled=parent.setNModeActive(null)
			Boolean t0=(!nModeSetEco)
			Boolean t1=(home && (!nestModeAway) )
			if( (t0 || t1) && NMecoisEnabled){
				LogAction("checkNestMode adjusting manager state NM is not setting eco", "warn", true)
				parent.setNModeActive(false)		// clear nMode has it in manager
			}
			if(t1){ state.nModeTstatLocAway=false }
			Boolean t2=(away && nestModeAway)
			if(nModeSetEco && t2 && (!NMecoisEnabled)){
				LogAction("checkNestMode adjusting manager state NM will clear eco", "warn", true)
				parent.setNModeActive(true)		// set nMode has it in manager
			}
			if(t2){ state.nModeTstatLocAway=true }

			Boolean homeChgd=false
			Boolean nestModeChgd=false
			if((Boolean)state.nModeLastHome != home){
				homeChgd=true;
				LogAction("NestMode Home Changed: ${homeChgd} Home: ${home}", "info", false)
				state.nModeLastHome=home
			}
			String t5=getNestLocPres()
			if((String)state.nModeLastNestMode != t5){
				nestModeChgd=true;
				String t6="info"
				if(!homeChgd){
					t6="warn"
				}
				LogAction("Nest location mode Changed: ${t5}", t6, true)
				state.nModeLastNestMode=t5
			}

			Boolean didsomething=false

// Manage state changes
			if(away && !nestModeAway){
				LogAction("checkNestMode: ${awayDesc} Nest 'Away' ${away}  ${nestModeAway}", "info", false)
				if(getnModeActionSec() < 4*60){
					LogAction("checkNestMode did change recently - SKIPPING", "warn", true)
					scheduleAutomationEval(90)
					storeExecutionHistory((now() - execTime), "checkNestMode")
					return
				}
				didsomething=true
				setAway(true)
				state.nModeLastNestMode="away"
				state.nModeTstatLocAway=true
				if(nModeSetEco){
					parent.setNModeActive(true) // set nMode has it in manager
					adjustEco(true, pName)
				}
				if(allowNotif){
					sendEventPushNotifications("${awayDesc} Nest 'Away'", "Info", pName)
				}
				if(nModeCamOnAway){ adjustCameras(true, pName) }

			}else if(home && nestModeAway){
				LogAction("checkNestMode: ${homeDesc} Nest 'Home' ${home}  ${nestModeAway}", "info", false)
				if(getnModeActionSec() < 4*60){
					LogAction("checkNestMode did change recently - SKIPPING", "warn", true)
					scheduleAutomationEval(90)
					storeExecutionHistory((now() - execTime), "checkNestMode")
					return
				}
				didsomething=true
				setAway(false)
				parent.setNModeActive(false)		// clear nMode has it in manager
				state.nModeLastNestMode="home"
				state.nModeTstatLocAway=false
				if(nModeSetEco){ adjustEco(false, pName) }
				if(allowNotif){
					sendEventPushNotifications("${homeDesc} Nest 'Home'", "Info", pName)
				}
				if(nModeCamOffHome){ adjustCameras(false, pName) }
			}
			else {
				LogAction("checkNestMode: No Changes | ${nModePresSensor ? "isPresenceHome: ${isPresenceHome(nModePresSensor)} | " : ""}ST-Mode: ($curStMode) | NestModeAway: ($nestModeAway) | Away: ($away) | Home: ($home)", "info", false)
			}
			if(didsomething){
				state.nModeActionDt=getDtNow()
				scheduleAutomationEval(90)
			}
			storeExecutionHistory((now() - execTime), "checkNestMode")
		}
	} catch (ex){
		log.error "checkNestMode Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "checkNestMode", true, getAutoType())
	}
}

String getNestLocPres(){
	if(getIsAutomationDisabled()){ return null}
	else {
		String plocationPresence=parent?.getLocationPresence()
		if(!plocationPresence){ return null }
		else {
			return plocationPresence
		}
	}
}

/********************************************************************************
|		SCHEDULE, MODE, or MOTION CHANGES ADJUST THERMOSTAT SETPOINTS			|
|		(AND THERMOSTAT MODE) AUTOMATION CODE									|
*********************************************************************************/

String getTstatAutoDevId(){
	if(settings.schMotTstat){ return settings.schMotTstat.deviceNetworkId.toString() }
	return null
}

private String tempRangeValues(){
	return (getTemperatureScale() == "C") ? "10..32" : "50..90"
}

private static List timeComparisonOptionValues(){
	return ["custom time", "midnight", "sunrise", "noon", "sunset"]
}

private static List timeDayOfWeekOptions(){
	return ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
}

private String getDayOfWeekName(Date date=null){
	if(!date){
		date=adjustTime()
	}
	Integer theDay=date.day
	List list=[]
	list=timeDayOfWeekOptions()
	//LogAction("theDay: $theDay date.date: ${date.day}")
	return(list[theDay])
}

/*
private getDayOfWeekNumber(date=null){
	if(!date){
		date=adjustTime(now())
	}
	if(date instanceof Date){
		return date.day
	}
	switch (date){
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
// TODO HE this may not be right
private Date adjustTime(time=null){
	if(time instanceof String){
		//get UTC time
		time=timeToday(time, location.timeZone).getTime()
	}
	if(time instanceof Date){
		//get unix time
		time=time.getTime()
	}
	if(!time){
		time=now()
	}
	if(time){
		return new Date(time)
//		return new Date(time + location.timeZone.getOffset(time))
	}
	return null
}

private String formatLocalTime(time, format="EEE, MMM d yyyy @ h:mm a z"){
	if(time instanceof Long){
		time=new Date(time)
	}
	if(time instanceof String){
		//get UTC time
		time=timeToday(time, location.timeZone)
	}
	if(!(time instanceof Date)){
		return null
	}
	def formatter=new java.text.SimpleDateFormat(format)
	formatter.setTimeZone(location.timeZone)
	return formatter.format(time)
}

private convertDateToUnixTime(date){
	if(!date){
		return null
	}
	if(!(date instanceof Date)){
		date=new Date(date)
	}
	//return date.time - location.timeZone.getOffset(date.time)
	return date.time
}

/*
private convertTimeToUnixTime(time){
	if(!time){
		return null
	}
	return time - location.timeZone.getOffset(time)
}
*/
private String formatTime(time, zone=null){
	//we accept both a Date or a settings' Time
	return formatLocalTime(time, "h:mm a${zone ? " z" : ""}")
}

private static String formatHour(h){
	return (h == 0 ? "midnight" : (h < 12 ? "${h} AM" : (h == 12 ? "noon" : "${h-12} PM"))).toString()
}

private static Map cleanUpMap(Map map){
	def washer=[]
	//find dirty laundry
	for (item in map){
		if(item.value == null) washer.push(item.key)
	}
	//clean it
	for (item in washer){
		map.remove(item)
	}
	washer=null
	return map
}

private buildDeviceNameList(devices, suffix){
	def cnt=1
	def result=""
	for (device in devices){
		def label=getDeviceLabel(device)
		result += "$label" + (cnt < devices.size() ? (cnt == devices.size() - 1 ? " $suffix " : ", ") : "")
		cnt++
	}
	if(result == ""){ result=null }
	return result
}

private String getDeviceLabel(device){
	return device instanceof String ? device : (device ? ( device.label ? device.label : (device.name ? device.name : "$device")) : "Unknown device")
}

Integer getCurrentSchedule(){
	Boolean noSched=false
	Integer mySched

	List schedList=getScheduleList()
	String res1
	Integer ccnt=1
	for (Integer cnt in schedList){
		res1=checkRestriction(cnt)
		if(res1 == null){ break }
		ccnt += 1
	}
	if(ccnt > schedList?.size()){ noSched=true }
	else { mySched=ccnt }
	if(mySched != null){
		LogTrace("getCurrentSchedule: mySched: $mySched noSched: $noSched ccnt: $ccnt res1: $res1")
	}
	return mySched
}

private String checkRestriction(Integer cnt){
	//LogTrace("checkRestriction:( $cnt )")
	String sLbl="schMot_${cnt}_"
	String restriction
	Boolean act=settings["${sLbl}SchedActive"]
	if(act){
		Map apprestrict=state."sched${cnt}restrictions"

		if(apprestrict && apprestrict.m && apprestrict.m.size() && !(location.mode.toString() in apprestrict.m)){
			restriction="a HE MODE mismatch"
		}else if(apprestrict && apprestrict.w && apprestrict.w.size() && !(getDayOfWeekName() in apprestrict.w)){
			restriction="a day of week mismatch"
		}else if(apprestrict && apprestrict.tf && apprestrict.tt && !(checkTimeCondition(apprestrict?.tf, apprestrict?.tfc, apprestrict?.tfo, apprestrict?.tt, apprestrict?.ttc, apprestrict?.tto))){
			restriction="a time of day mismatch"
		}else{
			if(settings["${sLbl}rstrctSWOn"]){
				for(sw in settings["${sLbl}rstrctSWOn"]){
					if(sw.currentValue("switch") != "on"){
						restriction="switch ${sw} being ${sw.currentValue("switch")}"
						break
					}
				}
			}
			if(!restriction && settings["${sLbl}rstrctSWOff"]){
				for(sw in settings["${sLbl}rstrctSWOff"]){
					if(sw.currentValue("switch") != "off"){
						restriction="switch ${sw} being ${sw.currentValue("switch")}"
						break
					}
				}
			}
			if(!restriction && settings["${sLbl}rstrctPHome"] && !isSomebodyHome(settings["${sLbl}rstrctPHome"])){
				for(pr in settings["${sLbl}rstrctPHome"]){
					if(!isPresenceHome(pr)){
						restriction="presence ${pr} being ${pr.currentValue("presence")}"
						break
					}
				}
			}
			if(!restriction && settings["${sLbl}rstrctPAway"] && isSomebodyHome(settings["${sLbl}rstrctPAway"])){
				for(pr in settings["${sLbl}rstrctPAway"]){
					if(isPresenceHome(pr)){
						restriction="presence ${pr} being ${pr.currentValue("presence")}"
						break
					}
				}
			}
		}
		LogTrace("checkRestriction:( $cnt ) restriction: $restriction")
	}else{
		restriction="an inactive schedule"
	}
	return restriction
}

public Map getActiveScheduleState(){
	return (Map)state.activeSchedData ?: null
}

Boolean getSchRestrictDoWOk(Integer cnt){
	Map apprestrict=(Map)state.activeSchedData
	Boolean result=true
	apprestrict?.each { sch ->
		if(sch?.key.toInteger() == cnt){
			if(!(getDayOfWeekName() in sch?.value?.w)){
				result=false
			}
		}
	}
	return result
}

Boolean checkTimeCondition(String timeFrom, String timeFromCustom, Integer timeFromOffset, String timeTo, String timeToCustom, Integer timeToOffset){
	Date time=adjustTime()
	//convert to minutes since midnight
	Integer tc=time.hours * 60 + time.minutes
	Integer tf
	Integer tt
	Integer i=0
	while (i < 2){
		Date t=null
		Integer h=null
		Integer m=null
		switch(i == 0 ? timeFrom : timeTo){
			case "custom time":
				t=adjustTime(i == 0 ? timeFromCustom : timeToCustom)
				if(i == 0){
					timeFromOffset=0
				}else{
					timeToOffset=0
				}
				break
			case "sunrise":
				t=getSunrise()
				break
			case "sunset":
				t=getSunset()
				break
			case "noon":
				h=12
				break
			case "midnight":
				h=(i == 0 ? 0 : 24)
			break
		}
		if(h != null){
			m=0
		}else{
			h=t.hours
			m=t.minutes
		}
		switch (i){
			case 0:
				tf=h * 60 + m + cast(timeFromOffset, "number")
				break
			case 1:
				tt=h * 60 + m + cast(timeFromOffset, "number")
				break
		}
		i += 1
	}
	//due to offsets, let's make sure all times are within 0-1440 minutes
	while(tf < 0) tf += 1440
	while(tf > 1440) tf -= 1440
	while(tt < 0) tt += 1440
	while(tt > 1440) tt -= 1440
	if(tf < tt){
		return (tc >= tf) && (tc < tt)
	}else{
		return (tc < tt) || (tc >= tf)
	}
}

private cast(value, String dataType){
	def trueStrings=["1", "on", "open", "locked", "active", "wet", "detected", "present", "occupied", "muted", "sleeping"]
	def falseStrings=["0", "false", "off", "closed", "unlocked", "inactive", "dry", "clear", "not detected", "not present", "not occupied", "unmuted", "not sleeping"]
	switch (dataType){
		case "string":
		case "text":
			if(value instanceof Boolean){
				return value ? "true" : "false"
			}
			return value ? "$value" : ""
		case "number":
			if(value == null) return (Integer) 0
			if(value instanceof String){
				if(value.isInteger())
					return value.toInteger()
				if(value.isFloat())
					return (Integer) Math.floor(value.toFloat())
				if(value in trueStrings)
					return (Integer) 1
			}
			def result=(Integer) 0
			try {
				result=(Integer) value
			} catch(all){
				result=(Integer) 0
			}
			return result ? result : (Integer) 0
		case "long":
			if(value == null) return 0L
			if(value instanceof String){
				if(value.isInteger())
					return (Long) value.toInteger()
				if(value.isFloat())
					return (Long) Math.round(value.toFloat())
				if(value in trueStrings)
					return 1L
			}
			def result=0L
			try {
				result=(Long) value
			} catch(all){
			}
			return result ? result : 0L
		case "decimal":
			if(value == null) return (float) 0
			if(value instanceof String){
				if(value.isFloat())
					return (float) value.toFloat()
				if(value.isInteger())
					return (float) value.toInteger()
				if(value in trueStrings)
					return (float) 1
			}
			def result=(float) 0
			try {
				result=(float) value
			} catch(all){
			}
			return result ? result : (float) 0
		case "boolean":
			if(value instanceof String){
				if(!value || (value in falseStrings))
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

@Field static Map svSunTFLD

private void initSunriseAndSunset(Map rtD){
        Map t0=svSunTFLD
        Long t=now()
        if(t0!=null){
                if(t<(Long)t0.nextM){
                        rtD.sunTimes=[:]+t0
                }else{ t0=null; svSunTFLD=null }
        }
        if(t0==null){
                Map sunTimes=app.getSunriseAndSunset()
                if(sunTimes.sunrise==null){
                        warn 'Actual sunrise and sunset times are unavailable; please reset the location for your hub', rtD
                        Long t1=timeToday('00:00', location.timeZone).getTime()
                        sunTimes.sunrise=new Date(Math.round(t1+7.0D*3600000.0D))
                        sunTimes.sunset=new Date(Math.round(t1+19.0D*3600000.0D))
                        t=0L
                }
                t0=[
                        s: sunTimes,
                        updated: t,
                        nextM: timeTodayAfter('23:59', '00:00', location.timeZone).getTime()
                ]
                if(t!=0L){
                        svSunTFLD=t0
                        if(eric())log.debug 'updating global sunrise'
                }
        }
}

//TODO is this expensive?
private Date getSunrise(){
	initSunriseAndSunset()
	Map sunTimes=svSunTFLD.s
	return adjustTime(sunTimes.sunrise)
}

private Date getSunset(){
	initSunriseAndSunset()
	Map sunTimes=svSunTFLD.s
	return adjustTime(sunTimes.sunset)
}

Boolean isTstatSchedConfigured(){
	//return (settings.schMotSetTstatTemp && state.activeSchedData.size())
	return !!(state.scheduleActiveCnt)
}

/* //NOT IN USE ANYMORE (Maybe we should keep for future use)
Boolean isTimeBetween(start, end, now, tz){
	Long startDt=Date.parse("E MMM dd HH:mm:ss z yyyy", start).getTime()
	Long endDt=Date.parse("E MMM dd HH:mm:ss z yyyy", end).getTime()
	Long nowDt=Date.parse("E MMM dd HH:mm:ss z yyyy", now).getTime()
	Boolean result=false
	if(nowDt > startDt && nowDt < endDt){
		result=true
	}
	//def result=timeOfDayIsBetween(startDt, endDt, nowDt, tz) ? true : false
	return result
}
*/

Boolean checkOnMotion(Integer mySched){
	LogTrace("checkOnMotion($mySched)")
	String sLbl="schMot_${mySched}_"

	if(settings["${sLbl}Motion"] && state."${sLbl}MotionActiveDt"){
		Boolean motionOn=isMotionActive(settings["${sLbl}Motion"])

		Long lastActiveMotionDt=Date.parse("E MMM dd HH:mm:ss z yyyy", state."${sLbl}MotionActiveDt").getTime()
		Integer lastActiveMotionSec=getMotionActiveSec(mySched)

		Long lastInactiveMotionDt=1L
		Integer lastInactiveMotionSec

		if(state."${sLbl}MotionInActiveDt"){
			lastInactiveMotionDt=Date.parse("E MMM dd HH:mm:ss z yyyy", state."${sLbl}MotionInActiveDt").getTime()
			lastInactiveMotionSec=getMotionInActiveSec(mySched)
		}

		LogAction("checkOnMotion: [ActiveDt: ${lastActiveMotionDt} (${lastActiveMotionSec} sec) | InActiveDt: ${lastInactiveMotionDt} (${lastInactiveMotionSec} sec) | MotionOn: ($motionOn)", "info", false)

		Integer ontimedelay=(settings."${sLbl}MDelayValOn"?.toInteger() ?: 60) * 1000		// default to 60s
		Integer offtimedelay=(settings."${sLbl}MDelayValOff"?.toInteger() ?: 30*60) * 1000	// default to 30 min

		Long ontimeNum=lastActiveMotionDt + ontimedelay
		Long offtimeNum=lastInactiveMotionDt + offtimedelay

		Long nowDt=now() // Date.parse("E MMM dd HH:mm:ss z yyyy", getDtNow()).getTime()
		if(ontimeNum > offtimeNum){  // means motion is on now, so ensure offtime is in future
			offtimeNum=nowDt + offtimedelay
		}

		Long lastOnTime			// if we are on now, backup ontime to not oscillate
		if(state."motion${mySched}UseMotionSettings" && state."motion${mySched}TurnedOnDt"){
			lastOnTime=Date.parse("E MMM dd HH:mm:ss z yyyy", state."motion${mySched}TurnedOnDt").getTime()
			if(ontimeNum > lastOnTime){
				ontimeNum=lastOnTime - ontimedelay
			}
		}

		String ontime=formatDt( ontimeNum )
		String offtime=formatDt( offtimeNum )

		LogAction("checkOnMotion: [ActiveDt: (${state."${sLbl}MotionActiveDt"}) | OnTime: ($ontime) | InActiveDt: (${state."${sLbl}MotionInActiveDt"}) | OffTime: ($offtime)]", "info", false)
		Boolean result=false
		if(nowDt >= ontimeNum && nowDt <= offtimeNum){
			result=true
		}
		if(nowDt < ontimeNum || (result && !motionOn)){
			LogAction("checkOnMotion: (Schedule $mySched - ${getSchedLbl(mySched)}) Scheduling Motion Check (60 sec)", "info", false)
			scheduleAutomationEval(60)
		}
		return result
	}
	return false
}

void setTstatTempCheck(){
	LogAction("setTstatTempCheck", "debug", false)
	/* NOTE:
		// This automation only works with Nest as it checks non-ST presence & thermostat capabilities
		// Presumes: That all thermostats in an automation are in the same Nest structure, so that all share home/away settings and tStat modes
	*/
	try {

		if(getIsAutomationDisabled()){ return }
		Long execTime=now()

		def tstat=settings.schMotTstat
		def tstatMir=settings.schMotTstatMir

		String pName=schMotPrefix()

		String curMode=tstat ? tstat?.currentThermostatMode.toString() : null

		String lastMode=state.schMotlastMode
		Boolean samemode=lastMode == curMode ? true : false

		Integer mySched=getCurrentSchedule()
		LogAction("setTstatTempCheck | Current Schedule: (${mySched ? ("${mySched} - ${getSchedLbl(mySched)}") : "None Active"})", "debug", false)
		Boolean noSched=(mySched == null) ? true : false

		Integer previousSched=state.schedLast
		Boolean samesched=previousSched == mySched ? true : false

		if((!samesched || !samemode) && previousSched){		// schedule change - set old schedule to not use motion
			if(state."motion${previousSched}UseMotionSettings"){
				LogAction("setTstatTempCheck: Disabled Motion Settings Used for Previous Schedule (${previousSched} - ${getSchedLbl(previousSched)}", "info", false)
			}
			state."motion${previousSched}UseMotionSettings"=false
			state."motion${previousSched}LastisBtwn"=false
		}

		if(!samesched || !samemode ){			// schedule change, clear out overrides
			disableOverrideTemps()
		}

		LogAction("setTstatTempCheck: [Current Schedule: (${getSchedLbl(mySched)}) | Previous Schedule: (${previousSched} - ${getSchedLbl(previousSched)}) | None: ($noSched)]", "info", false)

		if(noSched){
			LogAction("setTstatTempCheck: Skipping check [No matching Schedule]", "info", false)
		}else{
			Boolean isBtwn=checkOnMotion(mySched)
			Boolean previousBtwn=state."motion${mySched}LastisBtwn"
			state."motion${mySched}LastisBtwn"=isBtwn

			if(!isBtwn){
				if(state."motion${mySched}UseMotionSettings"){
					LogAction("setTstatTempCheck: Disabled Use of Motion Settings for Schedule (${mySched} - ${getSchedLbl(mySched)})", "info", false)
				}
				state."motion${mySched}UseMotionSettings"=false
			}

			String sLbl="schMot_${mySched}_"
			Boolean motionOn=isMotionActive(settings["${sLbl}Motion"])

			if(!state."motion${mySched}UseMotionSettings" && isBtwn && !previousBtwn){   // transitioned to use Motion
				if(motionOn){	// if motion is on use motion now
					state."motion${mySched}UseMotionSettings"=true
					state."motion${mySched}TurnedOnDt"=getDtNow()
					disableOverrideTemps()
					LogAction("setTstatTempCheck: Enabled Use of Motion Settings for schedule ${mySched}", "info", false)
				}else{
					state."${sLbl}MotionActiveDt"=null		// this will clear isBtwn
					state."motion${mySched}LastisBtwn"=false
					LogAction("setTstatTempCheck: Motion Sensors were NOT Active at Transition Time to Motion ON for Schedule (${mySched} - ${getSchedLbl(mySched)})", "info", false)
				}
			}

			Boolean samemotion=previousBtwn == isBtwn ? true : false
			Boolean schedMatch=(samesched && samemotion) ? true : false

			String strv="Using "
			if(schedMatch){ strv="" }
			LogAction("setTstatTempCheck: ${strv}Schedule ${mySched} (${previousSched}) use Motion settings: ${state."motion${mySched}UseMotionSettings"} | isBtwn: $isBtwn | previousBtwn: $previousBtwn | motionOn $motionOn", "debug", false)

			if(tstat && !schedMatch){
				def hvacSettings=state."sched${mySched}restrictions"
				Boolean useMotion=state."motion${mySched}UseMotionSettings"

				String newHvacMode=(!useMotion ? hvacSettings?.hvacm : (hvacSettings?.mhvacm ?: hvacSettings?.hvacm))
				if(newHvacMode && (newHvacMode != curMode)){

					if(newHvacMode == "rtnFromEco"){
						if(curMode == "eco"){
							String t0=tstat?.currentpreviousthermostatMode?.toString()
							if(t0){
								newHvacMode=t0
							}
						}else{
							newHvacMode=curMode
						}
						LogAction("setTstatTempCheck: New Mode is rtnFromEco; Setting Thermostat Mode to (${strCapitalize(newHvacMode)})", "info", false)
					}

					if(newHvacMode && (newHvacMode.toString() != curMode)){
						if(setTstatMode(settings.schMotTstat, newHvacMode, pName)){
							storeLastAction("Set ${tstat} Mode to ${strCapitalize(newHvacMode)}", getDtNow(), pName, tstat)
							LogAction("setTstatTempCheck: Setting ${tstat} Thermostat Mode to (${strCapitalize(newHvacMode)})", "info", false)
						}else{ LogAction("setTstatTempCheck: Error Setting ${tstat} Thermostat Mode to (${strCapitalize(newHvacMode)})", "warn", true) }
						if(tstatMir){
							if(setMultipleTstatMode(tstatMir, newHvacMode, pName)){
							LogAction("Mirroring (${newHvacMode}) to ${tstatMir}", "info", false)
							}
						}
					}
				}

				curMode=tstat?.currentThermostatMode?.toString()

				// if remote sensor is on, let it handle temp changes (above took care of a mode change)
				if(settings.schMotRemoteSensor && isRemSenConfigured()){
					state.schedLast=mySched
					state.schMotlastMode=curMode
					storeExecutionHistory((now() - execTime), "setTstatTempCheck")
					return
				}

				Boolean isModeOff=(curMode in ["off","eco"]) ? true : false
				String tstatHvacMode=curMode

				def heatTemp=null
				def coolTemp=null
				Boolean needChg=false

				if(!isModeOff && state.schMotTstatCanHeat){
					def oldHeat=getTstatSetpoint(tstat, "heat")
					heatTemp=getRemSenHeatSetTemp(curMode)
					if(heatTemp && oldHeat != heatTemp){
						needChg=true
						LogAction("setTstatTempCheck: Schedule Heat Setpoint '${heatTemp}${tUnitStr()}' on (${tstat}) | Old Setpoint: '${oldHeat}${tUnitStr()}'", "info", false)
						//storeLastAction("Set ${settings.schMotTstat} Heat Setpoint to ${heatTemp}", getDtNow(), pName, tstat)
					}else{ heatTemp=null }
				}

				if(!isModeOff && state.schMotTstatCanCool){
					def oldCool=getTstatSetpoint(tstat, "cool")
					coolTemp=getRemSenCoolSetTemp(curMode)
					if(coolTemp && oldCool != coolTemp){
						needChg=true
						LogAction("setTstatTempCheck: Schedule Cool Setpoint '${coolTemp}${tUnitStr()}' on (${tstat}) | Old Setpoint: '${oldCool}${tUnitStr()}'", "info", false)
						//storeLastAction("Set ${settings.schMotTstat} Cool Setpoint to ${coolTemp}", getDtNow(), pName, tstat)
					}else{ coolTemp=null }
				}
				if(needChg){
					if(setTstatAutoTemps(settings.schMotTstat, coolTemp?.toDouble(), heatTemp?.toDouble(), pName, tstatMir)){
						//LogAction("setTstatTempCheck: [Temp Change | newHvacMode: $newHvacMode | tstatHvacMode: $tstatHvacMode | heatTemp: $heatTemp | coolTemp: $coolTemp ]", "info", false)
						//storeLastAction("Set ${tstat} Cool Setpoint ${coolTemp} Heat Setpoint ${heatTemp}", getDtNow(), pName, tstat)
					}else{
						LogAction("setTstatTempCheck: Thermostat Set ERROR [ newHvacMode: $newHvacMode | tstatHvacMode: $tstatHvacMode | heatTemp: ${heatTemp}${tUnitStr()} | coolTemp: ${coolTemp}${tUnitStr()} ]", "warn", true)
					}
				}
			}
		}
		state.schedLast=mySched
		state.schMotlastMode=curMode
		storeExecutionHistory((now() - execTime), "setTstatTempCheck")
	} catch (ex){
		log.error "setTstatTempCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "setTstatTempCheck", true, getAutoType())
	}
}

/********************************************************************************
|				MASTER AUTOMATION FOR THERMOSTATS						|
*********************************************************************************/
static String schMotPrefix(){ return "schMot" }

def schMotModePage(){
//Logger("in schmotModePage")
	//def pName=schMotPrefix()
	dynamicPage(name: "schMotModePage", title: "Thermostat Automation", uninstall: false, install: true){
		def dupTstat
		def dupTstat1
		def dupTstat2
		def dupTstat3
		Boolean tStatPhys
//Logger("in schmotModePage0")
		String tempScale=getTemperatureScale()
		String tempScaleStr=tUnitStr()
		section("Configure Thermostat"){
			input name: "schMotTstat", type: "capability.thermostat", title: imgTitle(getAppImg("thermostat_icon.png"), inputTitleStr("Select Thermostat?")), multiple: false, submitOnChange: true, required: true
			//log.debug "schMotTstat: ${schMotTstat}"
			def tstat=settings.schMotTstat
			def tstatMir=settings.schMotTstatMir
			if(tstat){
//Logger("in schmotModePage1")
				getTstatCapabilities(tstat, schMotPrefix())
//Logger("in schmotModePage2")
				Boolean canHeat=state.schMotTstatCanHeat
				Boolean canCool=state.schMotTstatCanCool
				tStatPhys=tstat.currentNestType != "virtual"
//Logger("in schmotModePage3")

				String str=""
				def reqSenHeatSetPoint=getRemSenHeatSetTemp()
				def reqSenCoolSetPoint=getRemSenCoolSetTemp()
				def curZoneTemp=getRemoteSenTemp()
//Logger("in schmotModePage4")
				Integer t1=getCurrentSchedule()
				String tt0= getSchedLbl(t1)
				String tt1= tt0 ?: "Not Found"
				String tempSrcStr=(t1 && state.remoteTempSourceStr == "Schedule") ? "Schedule ${t1} (${tt1})" : "(${state.remoteTempSourceStr})"
//Logger("in schmotModePage5")

				str += tempSrcStr ? "Zone Status:\n• Temp Source:${tempSrcStr.length() > 15 ? "\n  └" : ""} ${tempSrcStr}" : ""
				str += curZoneTemp ? "\n• Temperature: (${curZoneTemp}${tempScaleStr})" : ""

				String hstr=canHeat ? "H: ${reqSenHeatSetPoint}${tempScaleStr}" : ""
				String cstr=canHeat && canCool ? "/" : ""
				cstr += canCool ? "C: ${reqSenCoolSetPoint}${tempScaleStr}" : ""
				str += "\n• Setpoints: (${hstr}${cstr})\n"

				str += "\nThermostat Status:\n• Temperature: (${getDeviceTemp(tstat)}${tempScaleStr})"
				hstr=canHeat ? "H: ${getTstatSetpoint(tstat, "heat")}${tempScaleStr}" : ""
				cstr=canHeat && canCool ? "/" : ""
				cstr += canCool ? "C: ${getTstatSetpoint(tstat, "cool")}${tempScaleStr}" : ""
				str += "\n• Setpoints: (${hstr}${cstr})"

				str += "\n• Mode: (${tstat ? ("${strCapitalize(tstat?.currentThermostatOperatingState)}/${strCapitalize(tstat?.currentThermostatMode)}") : "unknown"})"
				str += (state.schMotTstatHasFan) ? "\n• FanMode: (${strCapitalize(tstat?.currentThermostatFanMode)})" : "\n• No Fan on HVAC system"
				str += "\n• Presence: (${strCapitalize(getTstatPresence(tstat))})"
				def safetyTemps=getSafetyTemps(tstat)
					str += safetyTemps ? "\n• Safety Temps:\n  └ Min: ${safetyTemps.min}${tempScaleStr}/Max: ${safetyTemps.max}${tempScaleStr}" : ""
					str += "\n• Virtual: (${tstat?.currentNestType.toString() == "virtual" ? "True" : "False"})"
				paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("${tstat.displayName} Zone Status")), state: (str != "" ? "complete" : null)
				paragraph sectionTitleStr("${str}"), state: (str != "" ? "complete" : null)
//Logger("in schmotModePage6")

				String t0Str="ERROR:\nThe "
				String t1Str="Primary Thermostat was found in Mirror Thermostat List.\nPlease Correct to Proceed"
				if(!tStatPhys){      // if virtual thermostat, check if physical thermostat is in mirror list
					def mylist=[ deviceNetworkId:"${tstat.deviceNetworkId.toString().replaceFirst("v", "")}" ]
					dupTstat1=checkThermostatDupe(mylist, tstatMir)
					if(dupTstat1){
						paragraph imgTitle(getAppImg("i_err"), paraTitleStr("${t0Str}${t1Str}")), required: true, state: null
					}
				}else{		// if physcial thermostat, see if virtual version is in mirror list
					def mylist=[ deviceNetworkId:"v${tstat.deviceNetworkId.toString()}" ]
					dupTstat2=checkThermostatDupe(mylist, tstatMir)
					if(dupTstat2){
						paragraph imgTitle(getAppImg("i_err"), paraTitleStr("${t0Str}Virtual version of the ${t1Str}")), required: true, state: null
					}
				}
				dupTstat3=checkThermostatDupe(tstat, tstatMir)  // make sure thermostat is not in mirror list
				if(dupTstat3){
					paragraph imgTitle(getAppImg("i_err"), paraTitleStr("${t0Str}${t1Str}")), required: true, state: null
				}

				dupTstat=dupTstat1 || dupTstat2 ||  dupTstat3
				if(!tStatPhys){
				}
				input "schMotTstatMir", "capability.thermostat", title: imgTitle(getAppImg("thermostat_icon.png"), inputTitleStr("Mirror Changes to these Thermostats")), multiple: true, submitOnChange: true, required: false
				if(tstatMir && !dupTstat){
					tstatMir?.each { t ->
						paragraph "Thermostat Temp: ${getDeviceTemp(t)}${tempScaleStr}"//, image: " "
					}
				}
			}
		}

		if(settings.schMotTstat && !dupTstat){
			updateScheduleStateMap()
			section(){
				paragraph paraTitleStr("Choose Automations:"), required: false
				paragraph sectionTitleStr("The options below allow you to configure a thermostat with automations that will help save energy and maintain comfort"), required: false
			}

			section("Schedule Automation:"){
				Integer actSch=((Map)state.activeSchedData)?.size()
				String tDesc=(isTstatSchedConfigured() || ((Map)state.activeSchedData)?.size()) ? "Tap to modify Schedules" : null
				href "tstatConfigAutoPage1", title: imgTitle(getAppImg("schedule_icon.png"), inputTitleStr("Use Schedules to adjust Temp Setpoints and HVAC mode?")), description: (tDesc != null ? tDesc : ""), state: (tDesc != null ? "complete" : "")//, params: ["configType":"tstatSch"]
				if(actSch>0){
					Map schInfo=getScheduleDesc()
					if(schInfo?.size()){
						Integer curSch=getCurrentSchedule()
						schInfo?.each { schItem ->
							Integer schNum=schItem?.key
							def schDesc=schItem?.value
							Boolean schInUse=(curSch == schNum) ? true : false
							if(schNum && schDesc){
								href "schMotSchedulePage${schNum}", title: "", description: "${schDesc}\n\nTap to modify this Schedule", state: (schInUse ? "complete" : "")//, params: ["sNum":schNum]
							}
						}
					}
				}
			}

			String t3Str=" is not available on a VIRTUAL Thermostat"
			String t4Str="ERROR:\nThe Primary Thermostat is VIRTUAL and UNSUPPORTED for automation.\nPlease Correct to Proceed"
			section("Fan Control:"){
				if(tStatPhys || settings.schMotOperateFan){
					String desc=""
					String titStr=""
					if(state.schMotTstatHasFan){ titStr += "Use HVAC Fan for Circulation\nor\n" }
					titStr += "Run External Fan while HVAC is Operating"
					input (name: "schMotOperateFan", type: "bool", title: imgTitle(getAppImg("fan_control_icon.png"), inputTitleStr("${titStr}?")), description: desc, required: false, defaultValue: false, submitOnChange: true)
					String fanCtrlDescStr=""
					String t0=getFanSwitchDesc()
					if(settings.schMotOperateFan){
						fanCtrlDescStr += t0 ? "${t0}" : ""
						String fanCtrlDesc=isFanCtrlConfigured() ? "${fanCtrlDescStr}" + descriptions("d_ttm") : null
						href "tstatConfigAutoPage2", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Fan Control Config")), description: fanCtrlDesc ?: "Not Configured", state: (fanCtrlDesc ? "complete" : null), required: true//, params: ["configType":"fanCtrl"]
					}
				}else if(!tStatPhys){
					paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("Fan Control${t3Str}")), state: "complete"
				}
				if(!tStatPhys && settings.schMotOperateFan){ paragraph imgTitle(getAppImg("i_err"), paraTitleStr("FAN ${t4Str}")), required: true, state: null }
			}

			section("Remote Sensor:"){
				if(tStatPhys || settings.schMotRemoteSensor){
					String desc=""
					input (name: "schMotRemoteSensor", type: "bool", title: imgTitle(getAppImg("remote_sensor_icon.png"), inputTitleStr("Use Alternate Temp Sensors to Control Zone temperature?")), description: desc, required: false, defaultValue: false, submitOnChange: true)
					if(settings.schMotRemoteSensor){
						String remSenDescStr=""
						remSenDescStr += settings.remSenRuleType ? "Rule-Type: ${getEnumValue(remSenRuleEnum("heatcool"), settings.remSenRuleType)}" : ""
						remSenDescStr += settings.remSenTempDiffDegrees ? ("\n • Threshold: (${settings.remSenTempDiffDegrees}${tempScaleStr})") : ""
						remSenDescStr += settings.remSenTstatTempChgVal ? ("\n • Adjust Temp: (${settings.remSenTstatTempChgVal}${tempScaleStr})") : ""

						String hstr=remSenHeatTempsReq() ? "H: ${fixTempSetting(settings.remSenDayHeatTemp) ?: 0}${tempScaleStr}" : ""
						String cstr=remSenHeatTempsReq() && remSenCoolTempsReq() ? "/" : ""
						cstr += remSenCoolTempsReq() ? "C: ${fixTempSetting(settings.remSenDayCoolTemp) ?: 0}${tempScaleStr}" : ""
						remSenDescStr += (settings.remSensorDay && (settings.remSenDayHeatTemp || settings.remSenDayCoolTemp)) ? "\n • Default Temps:\n   └ (${hstr}${cstr})" : ""


						remSenDescStr += (settings.vthermostat) ? "\n\nVirtual Thermostat:" : ""
						remSenDescStr += (settings.vthermostat) ? "\n• Enabled" : ""

						//remote sensor/Day
						String dayModeDesc=""
						dayModeDesc += settings.remSensorDay ? "\n\nDefault Sensor${settings.remSensorDay?.size() > 1 ? "s" : ""}:" : ""
						Integer rCnt=settings.remSensorDay?.size()
						settings.remSensorDay?.each { t ->
							dayModeDesc += "\n ├ ${t?.label}: ${(t?.label?.toString()?.length() > 10) ? "\n │ └ " : ""}(${getDeviceTemp(t)}${tempScaleStr})"
						}
						dayModeDesc += settings.remSensorDay ? "\n └ Temp${(settings.remSensorDay?.size() > 1) ? " (avg):" : ":"} (${getDeviceTempAvg(settings.remSensorDay)}${tempScaleStr})" : ""
						remSenDescStr += settings.remSensorDay ? "${dayModeDesc}" : ""

						String remSenDesc=isRemSenConfigured() ? "${remSenDescStr}" + descriptions("d_ttm") : null
						href "tstatConfigAutoPage3", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Remote Sensor Config")), description: remSenDesc ?: "Not Configured", required: true, state: (remSenDesc ? "complete" : null)//, params: ["configType":"remSen"]
					}else{
						if(settings.vthermostat != null){
//ERS
							settingRemove("vthermostat")
							removeVstat("automation Selection")
						}
					}
				}else if(!tStatPhys){	paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("Remote Sensor${t3Str}")), state: "complete" }

				if(!tStatPhys && settings.schMotRemoteSensor){
					paragraph imgTitle(getAppImg("i_err"), paraTitleStr("Remote Sensor ${t4Str}")), required: true, state: null
				}
			}

			section("Leak Detection:"){
				if(tStatPhys || settings.schMotWaterOff){
					String desc=""
					input (name: "schMotWaterOff", type: "bool", title: imgTitle(getAppImg("leak_icon.png"), inputTitleStr("Turn Off if Water Leak is detected?")), description: desc, required: false, defaultValue: false, submitOnChange: true)
					if(settings.schMotWaterOff){
						String leakDesc=""
						String t0=leakWatSensorsDesc()
						leakDesc += (settings.leakWatSensors && t0) ? t0 : ""
						leakDesc += settings.leakWatSensors ? '\n\n'+autoStateDesc("leakWat") : ""
						leakDesc += (settings.leakWatSensors) ? "\n\nSettings:" : ""
						leakDesc += settings.leakWatOnDelay ? "\n • On Delay: (${getEnumValue(longTimeSecEnum(), settings.leakWatOnDelay)})" : ""
						//leakDesc += (settings.leakWatModes || settings.leakWatDays || (settings.leakWatStartTime && settings.leakWatStopTime)) ?
							//"\n • Restrictions Active: (${autoScheduleOk(leakWatPrefix()) ? "NO" : "YES"})" : ""
						String t1=getNotifConfigDesc(leakWatPrefix())
						leakDesc += t1 ? '\n\n'+t1 : ""
						leakDesc += (settings.leakWatSensors) ? descriptions("d_ttm") : ""
						String leakWatDesc=isLeakWatConfigured() ? leakDesc : null
						href "tstatConfigAutoPage4", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Leak Sensor Automation")), description: leakWatDesc ?: descriptions("d_ttc"), required: true, state: (leakWatDesc ? "complete" : null)//, params: ["configType":"leakWat"]
					}
				}else if(!tStatPhys){
					paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("Leak Detection${t3Str}")), state: "complete"
				}
				if(!tStatPhys && settings.schMotWaterOff){ paragraph imgTitle(getAppImg("i_err"), paraTitleStr("Leak ${t4Str}")), required: true, state: null }
			}
			section("Contact Automation:"){
				if(tStatPhys || settings.schMotContactOff){
					String desc=""
					input (name: "schMotContactOff", type: "bool", title: imgTitle(getAppImg("open_window.png"), inputTitleStr("Set ECO if Door/Window Contact Open?")), description: desc, required: false, defaultValue: false, submitOnChange: true)
					if(settings.schMotContactOff){
						String conDesc=""
						String t0=conWatContactDesc()
						conDesc += (settings.conWatContacts && t0) ? t0 : ""
						conDesc += settings.conWatContacts ? '\n\n'+autoStateDesc("conWat") : ""
						conDesc += settings.conWatContacts ? "\n\nSettings:" : ""
						conDesc += settings.conWatOffDelay ? "\n • Eco Delay: (${getEnumValue(longTimeSecEnum(), settings.conWatOffDelay)})" : ""
						conDesc += settings.conWatOnDelay ? "\n • On Delay: (${getEnumValue(longTimeSecEnum(), settings.conWatOnDelay)})" : ""
						conDesc += settings.conWatRestoreDelayBetween ? "\n • Delay Between Restores:\n   └ (${getEnumValue(longTimeSecEnum(), settings.conWatRestoreDelayBetween)})" : ""
						conDesc += (settings.conWatContacts) ? "\n • Restrictions Active: (${autoScheduleOk(conWatPrefix()) ? "NO" : "YES"})" : ""
						String t1=getNotifConfigDesc(conWatPrefix())
						conDesc += t1 ? '\n\n'+t1 : ""
						conDesc += (settings.conWatContacts) ? descriptions("d_ttm") : ""
						String conWatDesc=isConWatConfigured() ? "${conDesc}" : null

						href "tstatConfigAutoPage5", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Contact Sensors Config")), description: conWatDesc ?: descriptions("d_ttc"), required: true, state: (conWatDesc ? "complete" : null)//, params: ["configType":"conWat"]

					}
				}else if(!tStatPhys){
					paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("Contact automation${t3Str}")), state: "complete"
				}
				if(!tStatPhys && settings.schMotContactOff){
					paragraph imgTitle(getAppImg("i_err"), paraTitleStr("Contact ${t4Str}")), required: true, state: null
				}
			}
			section("Humidity Control:"){
				String desc=""
				input (name: "schMotHumidityControl", type: "bool", title: imgTitle(getAppImg("humidity_automation_icon.png"), inputTitleStr("Turn Humidifier On / Off?")), description: desc, required: false, defaultValue: false, submitOnChange: true)
				if(settings.schMotHumidityControl){
					String humDesc=""
					humDesc += (settings.humCtrlSwitches) ? humCtrlSwitchDesc() : ""
					humDesc += (settings.humCtrlHumidity) ? "${settings.humCtrlSwitches ? "\n\n" : ""}${humCtrlHumidityDesc()}" : ""
					humDesc += (settings.humCtrlUseWeather || settings.humCtrlTempSensor) ? "\n\nSettings:" : ""
					humDesc += (!settings.humCtrlUseWeather && settings.humCtrlTempSensor) ? "\n • Temp Sensor: (${getHumCtrlTemperature()}${tempScaleStr})" : ""
					humDesc += (settings.humCtrlUseWeather && !settings.humCtrlTempSensor) ? "\n • Weather: (${getHumCtrlTemperature()}${tempScaleStr})" : ""
					humDesc += (settings.humCtrlSwitches) ?  "\n • Restrictions Active: (${autoScheduleOk(humCtrlPrefix()) ? "NO" : "YES"})" : ""
			//TODO need this in schedule
					humDesc += ((settings.humCtrlTempSensor || settings.humCtrlUseWeather) ) ? descriptions("d_ttm") : ""
					String humCtrlDesc=isHumCtrlConfigured() ? "${humDesc}" : null
					href "tstatConfigAutoPage6", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("Humidifer Config")), description: humCtrlDesc ?: descriptions("d_ttc"), required: true, state: (humCtrlDesc ? "complete" : null)//, params: ["configType":"humCtrl"]
				}
			}
			section("External Temp:"){
				if(tStatPhys || settings.schMotExternalTempOff){
					String desc=""
					input (name: "schMotExternalTempOff", type: "bool", title: imgTitle(getAppImg("external_temp_icon.png"), inputTitleStr("Set ECO if External Temp is near comfort settings")), description: desc, required: false, defaultValue: false, submitOnChange: true)
					if(settings.schMotExternalTempOff){
						String extDesc=""
						extDesc += (settings.extTmpUseWeather || settings.extTmpTempSensor) ? autoStateDesc("extTmp")+'\n\n' : ""
						extDesc += (settings.extTmpUseWeather || settings.extTmpTempSensor) ? "Settings:" : ""
						extDesc += (!settings.extTmpUseWeather && settings.extTmpTempSensor) ? "\n • Sensor: (${getExtTmpTemperature()}${tempScaleStr})" : ""
						extDesc += (settings.extTmpUseWeather && !settings.extTmpTempSensor) ? "\n • Weather: (${getExtTmpTemperature()}${tempScaleStr})" : ""
					//TODO need this in schedule
						extDesc += settings.extTmpDiffVal ? "\n • Outside Threshold: (${settings.extTmpDiffVal}${tempScaleStr})" : ""
						extDesc += settings.extTmpInsideDiffVal ? "\n • Inside Threshold: (${settings.extTmpInsideDiffVal}${tempScaleStr})" : ""
						extDesc += settings.extTmpOffDelay ? "\n • ECO Delay: (${getEnumValue(longTimeSecEnum(), settings.extTmpOffDelay)})" : ""
						extDesc += settings.extTmpOnDelay ? "\n • On Delay: (${getEnumValue(longTimeSecEnum(), settings.extTmpOnDelay)})" : ""
						extDesc += (settings.extTmpTempSensor || settings.extTmpUseWeather) ? "\n • Restrictions Active: (${autoScheduleOk(extTmpPrefix()) ? "NO" : "YES"})" : ""
						String t0=getNotifConfigDesc(extTmpPrefix())
						extDesc += t0 ? "\n\n${t0}" : ""
						extDesc += ((settings.extTmpTempSensor || settings.extTmpUseWeather) ) ? descriptions("d_ttm") : ""
						String extTmpDesc=isExtTmpConfigured() ? "${extDesc}" : null
						href "tstatConfigAutoPage7", title: imgTitle(getAppImg("i_cfg"), inputTitleStr("External Temps Config")), description: extTmpDesc ?: descriptions("d_ttc"), required: true, state: (extTmpDesc ? "complete" : null)//, params: ["configType":"extTmp"]
					}
				}else if(!tStatPhys){
					paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("External Temp Automation${t3Str}")), state: "complete"
				}
				if(!tStatPhys && settings.schMotExternalTempOff){
					paragraph imgTitle(getAppImg("i_err"), paraTitleStr("External Temp ${t4Str}")), required: true, state: null
				}
			}

			section("Settings:"){
				input "schMotWaitVal", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Minimum Wait Time between Evaluations?")), required: false, defaultValue: 60, options: [30:"30 Seconds", 60:"60 Seconds",90:"90 Seconds",120:"120 Seconds"]
			}
		}
/*
		if(state.showHelp){
			section("Help:"){
				href url:"${getAutoHelpPageUrl()}", style:"embedded", required:false, title:"Help and Instructions", description:"", image: getAppImg("info.png")
			}
		}
*/
	}
}

String getSchedLbl(Integer num){
	String result=""
	if(num){
		Map schData=(Map)state.activeSchedData
		schData?.each { sch ->
			if(num == sch?.key.toInteger()){
				//log.debug "Label:(${sch?.value?.lbl})"
				result=sch?.value?.lbl
			}
		}
	}
	return result
}

def getSchedData(num){
	if(!num){ return null }
	def resData=[:]
	Map schData=(Map)state.activeSchedData
	schData?.each { sch ->
		//log.debug "sch: $sch"
		if(sch?.key != null && num?.toInteger() == sch?.key.toInteger()){
			// log.debug "Data:(${sch?.value})"
			resData=sch?.value
		}
	}
	return resData != [:] ? resData : null
}

/* NOTE
	Schedule Rules:
	You ALWAYS HAVE TEMPS in A SCHEDULE
	• You ALWAYS OFFER OPTION OF MOTION TEMPS in A SCHEDULE
	• If Motion is ENABLED, it MUST HAVE MOTION TEMPS
	• You ALWAYS OFFER RESTRICTION OPTIONS in A SCHEDULE
	• If REMSEN is ON, you offer remote sensors options
*/

def tstatConfigAutoPage1(params){ def t0=[:]; t0.configType="tstatSch"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage2(params){ def t0=[:]; t0.configType="fanCtrl"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage3(params){ def t0=[:]; t0.configType="remSen"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage4(params){ def t0=[:]; t0.configType="leakWat"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage5(params){ def t0=[:]; t0.configType="conWat"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage6(params){ def t0=[:]; t0.configType="humCtrl"; return tstatConfigAutoPage( t0 ) }
def tstatConfigAutoPage7(params){ def t0=[:]; t0.configType="extTmp"; return tstatConfigAutoPage( t0 ) }

def tstatConfigAutoPage(params){
	String configType=params?.configType
	if(params && params.configType){
		//Logger("tstatConfigAutoPage got params")
		state.tempTstatConfigPageData=params; configType=params.configType;
	}else{ configType=state.tempTstatConfigPageData?.configType }
	String pName=""
	String pTitle=""
	String pDesc=null
	switch(configType){
		case "tstatSch":
			pName=schMotPrefix()
			pTitle="Thermostat Schedule Automation"
			pDesc="Configure Schedules and Setpoints"
			break
		case "fanCtrl":
			pName=fanCtrlPrefix()
			pTitle="Fan Automation"
			break
		case "remSen":
			pName=remSenPrefix()
			pTitle="Remote Sensor Automation"
			break
		case "leakWat":
			pName=leakWatPrefix()
			pTitle="Thermostat/Leak Automation"
			break
		case "conWat":
			pName=conWatPrefix()
			pTitle="Thermostat/Contact Automation"
			break
		case "humCtrl":
			pName=humCtrlPrefix()
			pTitle="Humidifier Automation"
			break
		case "extTmp":
			pName=extTmpPrefix()
			pTitle="Thermostat/External Temps Automation"
			break
	}
	dynamicPage(name: "tstatConfigAutoPage", title: pTitle, description: pDesc, uninstall: false){
		def tstat=settings.schMotTstat
		if(tstat){
			String tempScale=getTemperatureScale()
			String tempScaleStr=tUnitStr()
			String tStatName=(String)tstat.displayName
			def tStatHeatSp=getTstatSetpoint(tstat, "heat")
			def tStatCoolSp=getTstatSetpoint(tstat, "cool")
			String tStatMode=tstat?.currentThermostatMode
			String tStatTemp="${getDeviceTemp(tstat)}${tempScaleStr}"
			Boolean canHeat=state.schMotTstatCanHeat
			Boolean canCool=state.schMotTstatCanCool
			//String locMode=location.mode.toString()

			List hidestr=null
			hidestr=["fanCtrl"]   // fan schedule is turned off
			if(!settings.schMotRemoteSensor){ // no remote sensors requested or used
				hidestr=["fanCtrl", "remSen"]
			}
			def params1= ["sData":["hideStr":hidestr]]
			state.t_tempSData=params1

			if(!settings.schMotOperateFan){

			}
			//if(!settings.schMotSetTstatTemp){   //motSen means no motion sensors offered   restrict means no restrictions offered  tstatTemp says no tstat temps offered
				//"tstatTemp", "motSen" "restrict"
			//}
			if(!settings.schMotExternalTempOff){
			}

			if(configType == "tstatSch"){
				section(){
					String str=""
					str += "• Temperature: (${tStatTemp})"
					str += "\n• Setpoints: (H: ${canHeat ? "${tStatHeatSp}${tempScaleStr}" : "NA"} / C: ${canCool ? "${tStatCoolSp}${tempScaleStr}" : "NA"})"
					paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("${tStatName}\nSchedules and Setpoints:")), state: "complete"
					paragraph sectionTitleStr("${str}"), state: "complete"
				}
				showUpdateSchedule(null, hidestr)
			}

			if(configType == "fanCtrl"){
				Boolean reqinp=!(settings["schMotCirculateTstatFan"] || settings["${pName}FanSwitches"])
				section("Control Fans/Switches based on Thermostat\n(3-Speed Fans Supported)"){
					input "${pName}FanSwitches", "capability.switch", title: imgTitle(getAppImg("fan_ventilation_icon.png"), inputTitleStr("Select Fan Switches?")), required: reqinp, submitOnChange: true, multiple: true
					if(settings."${pName}FanSwitches"){
						String t0=getFanSwitchDesc(false)
						paragraph paraTitleStr(t0), state: t0 ? "complete" : null
					}
				}
				if(settings["${pName}FanSwitches"]){
					section("Fan Event Triggers"){
						paragraph "Triggers are evaluated when Thermostat sends an operating event.  Poll time may take 1 minute or more for fan to switch on.",
								title: "What are these triggers?", image: getAppImg("i_inst")
						input "${pName}FanSwitchTriggerType", "enum", title: imgTitle(getAppImg("${settings."${pName}FanSwitchTriggerType" == 1 ? "thermostat" : "home_fan"}_icon.png"), inputTitleStr("Control Switches When?")), defaultValue: 1, options: switchRunEnum(), submitOnChange: true
						input "${pName}FanSwitchHvacModeFilter", "enum", title: imgTitle(getAppImg("i_mod"), inputTitleStr("Thermostat Mode Triggers?")), defaultValue: "any", options: fanModeTrigEnum(), submitOnChange: true, multiple: true
					}
					if(getFanSwitchesSpdChk()){
						section("Fan Speed Options"){
							input "${pName}FanSwitchSpeedCtrl", "bool", title: imgTitle(getAppImg("speed_knob_icon.png"), inputTitleStr("Enable Speed Control?")), defaultValue: true, submitOnChange: true
							if(settings["${pName}FanSwitchSpeedCtrl"]){
								paragraph paraTitleStr("What do these values mean?")
								paragraph sectionTitleStr("These threshold settings allow you to configure the speed of the fan based on it's closeness to the desired temp")
								input "${pName}FanSwitchLowSpeed", "decimal", title: imgTitle(getAppImg("fan_low_speed.png"), inputTitleStr( "Low Speed Threshold (${tempScaleStr})")), required: true, defaultValue: 1.0, submitOnChange: true
								input "${pName}FanSwitchMedSpeed", "decimal", title: imgTitle(getAppImg("fan_med_speed.png"), inputTitleStr("Medium Speed Threshold (${tempScaleStr})")), required: true, defaultValue: 2.0, submitOnChange: true
								input "${pName}FanSwitchHighSpeed", "decimal", title: imgTitle(getAppImg("fan_high_speed.png"), inputTitleStr("High Speed Threshold (${tempScaleStr})")), required: true, defaultValue: 4.0, submitOnChange: true
							}
						}
					}
				}
				if(state.schMotTstatHasFan || settings["${pName}FanSwitches"]){ // ERS allow for external fans also?
					section("Fan Circulation:"){
						String desc=""
						if(state.schMotTstatHasFan){
							input (name: "schMotCirculateTstatFan", type: "bool", title: imgTitle(getAppImg("fan_circulation_icon.png"), inputTitleStr("Run HVAC Fan for Circulation?")), description: desc, required: reqinp, defaultValue: false, submitOnChange: true)
						}
						if(settings["${pName}FanSwitches"]){
							input (name: "schMotCirculateExtFan", type: "bool", title: imgTitle(getAppImg("fan_circulation_icon.png"), inputTitleStr("Run External Fan for Circulation?")), description: desc, required: reqinp, defaultValue: false, submitOnChange: true)
						}else{
							settingRemove("schMotCirculateExtFan")
						}
						if(settings.schMotCirculateTstatFan || settings.schMotCirculateExtFan){
							input("schMotFanRuleType", "enum", title: imgTitle(getAppImg("rule_icon.png"), inputTitleStr("(Rule) Action Type")), options: remSenRuleEnum("fan"), required: true)
							paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("What is the Action Threshold Temp?"))
							paragraph sectionTitleStr("Temp difference to trigger Action Type.")
							def adjust=(getTemperatureScale() == "C") ? 0.5 : 1.0
							input "fanCtrlTempDiffDegrees", "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("Action Threshold Temp (${tempScaleStr})")), required: true, defaultValue: adjust
							input name: "fanCtrlOnTime", type: "enum", title: imgTitle(getAppImg("timer_icon.png"), inputTitleStr("Minimum circulate Time\n(Optional)")), defaultValue: 240, options: fanTimeSecEnum(), required: true, submitOnChange: true
							input "fanCtrlTimeBetweenRuns", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Delay Between On/Off Cycles\n(Optional)")), required: true, defaultValue: 1200, options: longTimeSecEnum(), submitOnChange: true

						}
					}
				}
				section(getDmtSectionDesc(fanCtrlPrefix())){
					String pageDesc=getDayModeTimeDesc(pName)
					href "setDayModeTimePage2", title: imgTitle(getAppImg("i_calf"),inputTitleStr(titles("t_cr"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName": "${pName}"]
				}

				if(settings."${pName}FanSwitches"){
					String schTitle
					if(!((Map)state.activeSchedData)?.size()){
						schTitle="Optionally create schedules to set temperatures based on schedule"
					}else{
						schTitle="Temperature settings based on schedule"
					}
					section(schTitle){ // FANS USE TEMPS IN LOGIC
						href "scheduleConfigPage", title: imgTitle(getAppImg("schedule_icon.png"), inputTitleStr("Enable/Modify Schedules")), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["sData":["hideStr":"${hidestr}"]]
					}
				}
			}

			Boolean cannotLock
			double defHeat
			double defCool
			double curTemp
			if(!getMyLockId()){
				setMyLockId(app.id)
			}
//ERS
// this deals with changing the tstat on the automation
			if(state.remSenTstat){
				if(tstat.deviceNetworkId != state.remSenTstat){
					removeVstat("settings pages")
				}
			}
			if(settings.schMotRemoteSensor){
				if(parent.remSenLock(tstat?.deviceNetworkId, getMyLockId()) ){  // lock new ID
					state.remSenTstat=tstat?.deviceNetworkId
					cannotLock=false
				}else{ cannotLock=true }
			}

			if(configType == "remSen"){
				if(cannotLock){
					section(""){
						paragraph imgTitle(getAppImg("i_err"), paraTitleStr("Cannot Lock thermostat for remote sensor - thermostat may already be in use.  Please Correct")), required: true, state: null
					}
					settingRemove("vthermostat")
				}

				if(!cannotLock){
					section("Select the Allowed (Rule) Action Type:"){
						if(!settings.remSenRuleType){
							paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("What are Rule Actions?"))
							paragraph sectionTitleStr("They determine the actions taken when the temperature threshold is reached, to balance temperatures")
						}
						input(name: "remSenRuleType", type: "enum", title: imgTitle(getAppImg("rule_icon.png"), inputTitleStr("(Rule) Action Type")), options: remSenRuleEnum("heatcool"), required: true, submitOnChange: true)
					}
					if(settings.remSenRuleType){
						String senLblStr="Default"
						section("Choose Temperature Sensor(s) to use:"){
							Boolean daySenReq= !settings.remSensorDay
							input "remSensorDay", "capability.temperatureMeasurement", title: imgTitle(getAppImg("i_t"), inputTitleStr("${senLblStr} Temp Sensor(s)")), submitOnChange: true, required: daySenReq, multiple: true
							if(settings.remSensorDay){
								curTemp=getDeviceTempAvg(settings.remSensorDay)
								String tt0=settings["${sLbl}remSensorDay"]?.size() > 1 ? " (avg):" : ":"
								String tmpVal="Temp${tt0} (${curTemp} ${tempScaleStr})"
								//String tmpVal="Temp${(settings.remSensorDay?.size() > 1) ? " (avg):" : ":"} (${curTemp}${tempScaleStr})"
								if(settings.remSensorDay.size() > 1){
									href "remSenShowTempsPage", title: inputTitleStr("View ${senLblStr} Sensor Temps"), description: tmpVal, state: "complete"
									paragraph imgTitle(getAppImg("i_icon.png"), paraTitleStr("Multiple temp sensors will return the average of those sensors."))
								}else{
									 paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("Remote Sensor Temp")), state: "complete"
									 paragraph sectionTitleStr(tmpVal), state: "complete"
								}
							}
						}
						if(settings.remSensorDay){
							section("Desired Setpoints"){
								paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("What are these temps for?"))
								paragraph sectionTitleStr("These temps are used when remote sensors are enabled and no schedules are created or active")
								if(isTstatSchedConfigured()){
//								if(settings.schMotSetTstatTemp){
									paragraph "If schedules are enabled and that schedule is in use it's setpoints will take precendence over the setpoints below", required: true, state: null
								}
								String tempStr="Default "
								if(remSenHeatTempsReq()){
									defHeat=fixTempSetting(getGlobalDesiredHeatTemp())
									defHeat=defHeat ?: (tStatHeatSp ?: curTemp-1)
									input "remSenDayHeatTemp", "decimal", title: imgTitle(getAppImg("heat_icon.png"), inputTitleStr("Desired ${tempStr}Heat Temp (${tempScaleStr})")), description: "Range within ${tempRangeValues()}", range: tempRangeValues(), required: true, defaultValue: defHeat
								}
								if(remSenCoolTempsReq()){
									defCool=fixTempSetting(getGlobalDesiredCoolTemp())
									defCool=defCool ?: (tStatCoolSp ?: curTemp+1)
									input "remSenDayCoolTemp", "decimal", title: imgTitle(getAppImg("cool_icon.png"), inputTitleStr("Desired ${tempStr}Cool Temp (${tempScaleStr})")), description: "Range within ${tempRangeValues()}", range: tempRangeValues(), required: true, defaultValue: defCool
								}
							}
							section("Remote Sensor Settings"){
								paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("What is the Action Threshold Temp?"))
								paragraph sectionTitleStr("Temp difference to trigger Actions.")
								input "remSenTempDiffDegrees", "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("Action Threshold Temp (${tempScaleStr})")), required: true, defaultValue: 2.0
								if(settings.remSenRuleType != "Circ"){
									paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("What are Temp Increments?"))
									paragraph sectionTitleStr("Is the amount the thermostat temp is adjusted +/- to turn on/off the HVAC system.")
									input "remSenTstatTempChgVal", "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("Change Temp Increments (${tempScaleStr})")), required: true, defaultValue: 5.0
								}
							}

							section("(Optional) Create a Virtual Nest Thermostat:"){
								input(name: "vthermostat", type: "bool", title: imgTitle(getAppImg("thermostat_icon.png"), inputTitleStr("Create Virtual Nest Thermostat")), required: false, submitOnChange: true)
								if(settings.vthermostat!=null && !parent.addRemoveVthermostat((String)tstat.deviceNetworkId, settings.vthermostat, getMyLockId())){
									paragraph imgTitle(getAppImg("i_err"), paraTitleStr("Unable to ${(settings.vthermostat ? "enable" : "disable")} Virtual Thermostat!. Please Correct"))
								}
							}

							String schTitle
							if(!((Map)state.activeSchedData)?.size()){
								schTitle="Optionally create schedules to set temperatures, alternate sensors based on schedule"
							}else{
								schTitle="Temperature settings and optionally alternate sensors based on schedule"
							}
							section(schTitle){
								href "scheduleConfigPage", title: imgTitle(getAppImg("schedule_icon.png"), inputTitleStr("Enable/Modify Schedules")), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["sData":["hideStr":"${hidestr}"]]
							}
						}
					}
				}
			}

			if(configType == "leakWat"){
				section("When Leak is Detected, Turn Off this Thermostat"){
					Boolean req=(settings.leakWatSensors || settings.schMotTstat)
					input name: "leakWatSensors", type: "capability.waterSensor", title: imgTitle(getAppImg("water_icon.png"), inputTitleStr("Which Leak Sensor(s)?")), multiple: true, submitOnChange: true, required: req
					if(settings.leakWatSensors){
						paragraph imgTitle(getAppImg("i_inst"), paraTitleStr(leakWatSensorsDesc())), state: "complete"
					}
				}
				if(settings.leakWatSensors){
					section("Restore On when Dry:"){
						input "${pName}OnDelay",  "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr(titles("t_dr"))), required: false, defaultValue: 300, options: longTimeSecEnum(), submitOnChange: true // Delay Restore
					}
					section(sectionTitleStr(titles("t_nt"))){
						String t0=getNotifConfigDesc(pName)
						String pageDesc=t0 ? t0 + descriptions("d_ttm") : ""
						href "setNotificationPage3", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
					}
				}
			}

			if(configType == "conWat"){
				section("When these Contacts are open, Set this Thermostat to ECO"){
					Boolean req=!settings.conWatContacts
					input name: "conWatContacts", type: "capability.contactSensor", title: imgTitle(getAppImg("contact_icon.png"), inputTitleStr("Which Contact(s)?")), multiple: true, submitOnChange: true, required: req
					if(settings.conWatContacts){
						String str=""
						str += settings.conWatContacts ? conWatContactDesc()+'\n' : ""
						paragraph imgTitle(getAppImg("i_inst"), paraTitleStr(str)), state: (str != "" ? "complete" : null)
					}
				}
				if(settings.conWatContacts){
					section("Delay Values:"){
						input "${pName}OffDelay", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr(titles("t_dtse"))), required: false, defaultValue: 300, options: longTimeSecEnum(), submitOnChange: true // Delay to set ECO

						input "${pName}OnDelay",  "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr(titles("t_dr"))), required: false, defaultValue: 300, options: longTimeSecEnum(), submitOnChange: true

						input "conWatRestoreDelayBetween", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Delay Between On/Eco Cycles\n(Optional)")), required: false, defaultValue: 600, options: longTimeSecEnum(), submitOnChange: true

					}
					section("Restoration Preferences:"){
						input "${pName}OffTimeout", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Auto Restore after (Optional)")), defaultValue: 0, options: longTimeSecEnum(), required: false, submitOnChange: true
						if(!settings."${pName}OffTimeout"){ state."${pName}TimeoutScheduled"=false }
					}

					section(getDmtSectionDesc(conWatPrefix())){
						String pageDesc=getDayModeTimeDesc(pName)
						href "setDayModeTimePage3", title: imgTitle(getAppImg("i_calf"),inputTitleStr(titles("t_cr"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName": "${pName}"]
					}
					section(sectionTitleStr(titles("t_nt"))){
						String t0=getNotifConfigDesc(pName)
						String pageDesc=t0 ? t0 + descriptions("d_ttm") : ""
						href "setNotificationPage4", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
					}
				}
			}

			if(configType == "humCtrl"){
				section("Switch for Humidifier"){
					def reqinp=!(settings.humCtrlSwitches)
// TODO needs new icon
					input "humCtrlSwitches", "capability.switch", title: imgTitle(getAppImg("fan_ventilation_icon.png"), inputTitleStr("Select Switches?")), required: reqinp, submitOnChange: true, multiple: true

/*
this does not work...
*/
					def t00=settings[humCtrlSwitches].collect { it?.id }
					def t1=state.oldhumCtrlSwitches
					def t2=t1.collect { it?.id }
					if(t2 != t00){
						state.haveRunHumidifier=false
						if(settings.humCtrlSwitches){ humCtrlSwitches*.off() }
						if(t1){ t1*.off() }
						state.oldhumCtrlSwitches=settings.humCtrlSwitches
						log.warn "found different oldhum vs. humctrlSwitches"
					}

					if(settings.humCtrlSwitches){
						String t0=humCtrlSwitchDesc(false)
						paragraph paraTitleStr("${t0}"), state: t0 ? "complete" : null
					}
				}
				if(settings.humCtrlSwitches){
					section("Humidifier Triggers"){
						paragraph imgTitle(getAppImg("i_inst"), paraTitleStr( "What are these triggers?"))
						paragraph sectionTitleStr("Triggers are evaluated when Thermostat sends an operating event.  Poll time may take 1 minute or more for fan to switch on.")
// TODO needs to fix icon
						input "humCtrlSwitchTriggerType", "enum", title: imgTitle(getAppImg("${settings.humCtrlSwitchTriggerType == 1 ? "thermostat" : "home_fan"}_icon.png"), inputTitleStr("Control Switches When?")), defaultValue: "5", options: switchRunEnum(true), submitOnChange: true
						input "humCtrlSwitchHvacModeFilter", "enum", title: imgTitle(getAppImg("i_mod"), inputTitleStr("Thermostat Mode Triggers?")), defaultValue: "any", options: fanModeTrigEnum(),
								submitOnChange: true, multiple: true
					}
					section("Indoor Humidity Measurement"){
						Boolean req=!settings.humCtrlHumidity ? true : false
						input name: "humCtrlHumidity", type: "capability.relativeHumidityMeasurement", title: imgTitle(getAppImg("humidity_icon.png"), inputTitleStr("Which Humidity Sensor(s)?")), multiple: true, submitOnChange: true, required: req
						if(settings.humCtrlHumidity){
							String str=""
							str += settings.humCtrlHumidity ? "${humCtrlHumidityDesc()}\n" : ""
							paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("${str}")), state: (str != "" ? "complete" : null)
						}
					}
					section("Select the External Temp Sensor to Use:"){
						if(!parent.getSettingVal("weatherDevice")){
							paragraph "Please Enable the Weather Device under the Manager App before trying to use External Weather as the External Temperature Sensor!", required: true, state: null
						}else{
							if(!settings.humCtrlTempSensor){
								input "humCtrlUseWeather", "bool", title: imgTitle(getAppImg("weather_icon.png"), inputTitleStr("Use Local Weather as External Sensor?")), required: false, defaultValue: false, submitOnChange: true
								//state.needWeathUpd=true
								if(settings.humCtrlUseWeather){
									if(state.curWeather == null){
										getExtConditions()
									}
									def tmpVal=(tempScale == "C") ? state.curWeaTemp_c : state.curWeaTemp_f
									paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("Local Weather:\n• ${state.curWeatherLoc}\n• Temp: (${tmpVal}${tempScaleStr})")), state: "complete"
								}
							}
						}
						if(!settings.humCtrlUseWeather){
							state.curWeather=null  // force refresh of weather if toggled
							Boolean senReq=(!settings.humCtrlUseWeather && !settings.humCtrlTempSensor) ? true : false
							input "humCtrlTempSensor", "capability.temperatureMeasurement", title: imgTitle(getAppImg("i_t"), inputTitleStr("Select a Temp Sensor?")), submitOnChange: true, multiple: false, required: senReq
							if(settings.humCtrlTempSensor){
								String str=""
								str += settings.humCtrlTempSensor ? "Sensor Status:" : ""
								str += settings.humCtrlTempSensor ? "\n└ Temp: (${settings.humCtrlTempSensor?.currentTemperature}${tempScaleStr})" : ""
								paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("${str}")), state: (str != "" ? "complete" : null)
							}
						}
					}
					section(getDmtSectionDesc(humCtrlPrefix())){
						String pageDesc=getDayModeTimeDesc(pName)
						href "setDayModeTimePage4", title: imgTitle(getAppImg("i_calf"),inputTitleStr(titles("t_cr"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName": "${pName}"]
					}
				}
			}

			if(configType == "extTmp"){
				section("Select the External Temps to Use:"){
					if(!parent.getSettingVal("weatherDevice")){
						paragraph "Please Enable the Weather Device under the Manager App before trying to use External Weather as an External Sensor!", required: true, state: null
					}else{
						if(!settings.extTmpTempSensor){
							input "extTmpUseWeather", "bool", title: imgTitle(getAppImg("weather_icon.png"), inputTitleStr("Use Local Weather as External Sensor?")), required: false, defaultValue: false, submitOnChange: true
							//state.needWeathUpd=true
							if(settings.extTmpUseWeather){
								if(state.curWeather == null){
									getExtConditions()
								}
								def tmpVal=(tempScale == "C") ? state.curWeaTemp_c : state.curWeaTemp_f
								def curDp=getExtTmpDewPoint()
								paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("Local Weather:")), state: "complete"
								paragraph sectionTitleStr("• ${state.curWeatherLoc}\n• Temp: (${tmpVal}${tempScaleStr})\n• Dewpoint: (${curDp}${tempScaleStr})"), state: "complete"
							}
						}
					}
					if(!settings.extTmpUseWeather){
						state.curWeather=null  // force refresh of weather if toggled
						Boolean senReq=(!settings.extTmpUseWeather && !settings.extTmpTempSensor)
						input "extTmpTempSensor", "capability.temperatureMeasurement", title: imgTitle(getAppImg("i_t"), inputTitleStr("Select a Temp Sensor?")), submitOnChange: true, multiple: false, required: senReq
						if(settings.extTmpTempSensor){
							String str=""
							str += settings.extTmpTempSensor ? "Sensor Status:" : ""
							str += settings.extTmpTempSensor ? "\n└ Temp: (${settings.extTmpTempSensor?.currentTemperature}${tempScaleStr})" : ""
							paragraph imgTitle(getAppImg("i_inst"), paraTitleStr(str)), state: (str != "" ? "complete" : null)
						}
					}
				}
				if(settings.extTmpUseWeather || settings.extTmpTempSensor){
					section("When the threshold Temps are Reached\nSet the Thermostat to ECO"){
						input name: "extTmpDiffVal", type: "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("When desired and external temp difference is at least this many degrees (${tempScaleStr})?")), defaultValue: 1.0, submitOnChange: true, required: true
						input name: "extTmpInsideDiffVal", type: "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("AND When desired and internal temp difference is within this many degrees (${tempScaleStr})?")), defaultValue: getTemperatureScale() == "C" ? 2.0 : 4.0, submitOnChange: true, required: true
					}
					section("Delay Values:"){

						input "${pName}OffDelay", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr(titles("t_dtse"))), required: false, defaultValue: 300, options: longTimeSecEnum(), submitOnChange: true  // Delay to set eco

						input "${pName}OnDelay", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr(titles("t_dr"))), required: false, defaultValue: 300, options: longTimeSecEnum(), submitOnChange: true

					}
					section("Restoration Preferences:"){
						input "${pName}OffTimeout", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Auto Restore after (Optional)")), defaultValue: 0, options: longTimeSecEnum(), required: false, submitOnChange: true
						if(!settings."${pName}OffTimeout"){ state."${pName}TimeoutScheduled"=false }
					}
					section(getDmtSectionDesc(extTmpPrefix())){
						String pageDesc=getDayModeTimeDesc(pName)
						href "setDayModeTimePage5", title: imgTitle(getAppImg("i_calf"),inputTitleStr(titles("t_cr"))), description: pageDesc, state: (pageDesc ? "complete" : null)//,params: ["pName": "${pName}"]
					}
					section(sectionTitleStr(titles("t_nt"))){
						String t0=getNotifConfigDesc(pName)
						String pageDesc=t0 ? t0 + descriptions("d_ttm") : ""
						href "setNotificationPage5", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
					}
					String schTitle
					if(!((Map)state.activeSchedData)?.size()){
						schTitle="Optionally create schedules to set temperatures based on schedule"
					}else{
						schTitle="Temperature settings based on schedule"
					}
					section(schTitle){ // EXTERNAL TEMPERATURE has TEMP Setting
						href "scheduleConfigPage", title: imgTitle(getAppImg("schedule_icon.png"), inputTitleStr("Enable/Modify Schedules")), description: pageDesc, state: (pageDesc ? "complete" : null)//, params: ["sData":["hideStr":"${hidestr}"]]
					}
				}
			}
		}
	}
}

def scheduleConfigPage(params){
	//LogTrace("scheduleConfigPage ($params)")
	def sData=params?.sData
	if(params && params.sData){
		state.t_tempSData=params
		sData=params.sData
	}else{
		sData=state.t_tempSData?.sData
	}
	dynamicPage(name: "scheduleConfigPage", title: "Thermostat Schedule Page", description: "Configure/View Schedules", uninstall: false){
		if(settings.schMotTstat){
			def tstat=settings.schMotTstat
			Boolean canHeat=state.schMotTstatCanHeat
			Boolean canCool=state.schMotTstatCanCool
			String str=""
			def reqSenHeatSetPoint=getRemSenHeatSetTemp()
			def reqSenCoolSetPoint=getRemSenCoolSetTemp()
			def curZoneTemp=getRemoteSenTemp()
			String tempSrcStr=state.remoteTempSourceStr
			String tempScaleStr=tUnitStr()
			section(){
				str += "Zone Status:\n• Temp Source: (${tempSrcStr})\n• Temperature: (${curZoneTemp}${tempScaleStr})"

				String hstr=canHeat ? "H: ${reqSenHeatSetPoint}${tempScaleStr}" : ""
				String cstr=canHeat && canCool ? "/" : ""
				cstr += canCool ? "C: ${reqSenCoolSetPoint}${tempScaleStr}" : ""
				str += "\n• Setpoints: (${hstr}${cstr})\n"

				str += "\nThermostat Status:\n• Temperature: (${getDeviceTemp(tstat)}${tempScaleStr})"
				String tt0=tstat ? "${strCapitalize(tstat?.currentThermostatOperatingState)}/${strCapitalize(tstat?.currentThermostatMode)}" : "unknown"
				str += "\n• Mode: (${tt0})"

				hstr=canHeat ? "H: ${getTstatSetpoint(tstat, "heat")}${tempScaleStr}" : ""
				cstr=canHeat && canCool ? "/" : ""
				cstr += canCool ? "C: ${getTstatSetpoint(tstat, "cool")}${tempScaleStr}" : ""
				str += "\n• Setpoints: (${hstr}${cstr})"

				str += (state.schMotTstatHasFan) ? "\n• FanMode: (${strCapitalize(tstat?.currentThermostatFanMode)})" : "\n• No Fan on HVAC system"
				str += "\n• Presence: (${strCapitalize(getTstatPresence(tstat))})"
				paragraph imgTitle(getAppImg("info_icon2.png"), paraTitleStr("${tstat?.displayName}\nSchedules and Setpoints:")), state: "complete"
				paragraph sectionTitleStr("${str}"), state: "complete"
			}
			showUpdateSchedule(null,sData?.hideStr)
		}
	}
}

//ERS
def schMotSchedulePage1(params){ def t0=[:]; t0.sNum=1; return schMotSchedulePage( t0 ) }
def schMotSchedulePage2(params){ def t0=[:]; t0.sNum=2; return schMotSchedulePage( t0 ) }
def schMotSchedulePage3(params){ def t0=[:]; t0.sNum=3; return schMotSchedulePage( t0 ) }
def schMotSchedulePage4(params){ def t0=[:]; t0.sNum=4; return schMotSchedulePage( t0 ) }
def schMotSchedulePage5(params){ def t0=[:]; t0.sNum=5; return schMotSchedulePage( t0 ) }
def schMotSchedulePage6(params){ def t0=[:]; t0.sNum=6; return schMotSchedulePage( t0 ) }
def schMotSchedulePage7(params){ def t0=[:]; t0.sNum=7; return schMotSchedulePage( t0 ) }
def schMotSchedulePage8(params){ def t0=[:]; t0.sNum=8; return schMotSchedulePage( t0 ) }

def schMotSchedulePage(params){
	//LogTrace("schMotSchedulePage($params)")
	Integer sNum=params?.sNum
	if(params?.sNum){
		state.t_schedData=params
		sNum=params?.sNum
	}else{
		sNum=state.t_schedData?.sNum
	}
	dynamicPage(name: "schMotSchedulePage", title: "Edit Schedule Page", description: "Modify Schedules", uninstall: false){
		if(sNum){
			showUpdateSchedule(sNum)
		}
	}
}

List getScheduleList(){
	def cnt=null // parent ? parent?.state?.appData?.settings.schedules?.count : null
	Integer maxCnt=cnt ? cnt.toInteger() : 8
	maxCnt=Math.min( Math.max(maxCnt,4), 8)
	if(maxCnt < state.scheduleList?.size()){
		maxCnt=state.scheduleList?.size()
		LogAction("A schedule size issue has occurred. The configured schedule size is smaller than the previous configuration restoring previous schedule size.", "warn", true)
	}
	List list=1..maxCnt
	state.scheduleList=list
	return list
}

def showUpdateSchedule(Integer sNum=null, List hideStr=null){
	updateScheduleStateMap()
	List schedList=getScheduleList()  // setting in initAutoApp adjust # of schedule slots
	Boolean lact
	Boolean act=true
	String sLbl
	Integer cnt=1
	schedList?.each { Integer scd ->
		sLbl="schMot_${scd}_"
		if(sNum != null){
			if(sNum == scd){
				lact=act
				act=settings["${sLbl}SchedActive"]
				String schName=settings["${sLbl}name"]
				editSchedule("secData":["scd":scd, "schName":schName, "hideable":(sNum ? false : true), "hidden":(act || (!act && scd == 1)) ? true : false, "hideStr":hideStr])
			}
		}else{
			lact=act
			act=settings["${sLbl}SchedActive"]
			if(lact || act){
				String schName=settings["${sLbl}name"]
				editSchedule("secData":["scd":scd, "schName":schName, "hideable":true, "hidden":(act || (!act && scd == 1)) ? true : false, "hideStr":hideStr])
			}
		}
	}
}

def editSchedule(Map schedData){
	Integer cnt=schedData?.secData?.scd
	LogTrace("editSchedule (${schedData?.secData})")

	String sLbl="schMot_${cnt}_"
	Boolean canHeat=state.schMotTstatCanHeat
	Boolean canCool=state.schMotTstatCanCool
	String tempScaleStr=tUnitStr()
	Boolean act=settings["${sLbl}SchedActive"]
	String actIcon=act ? "active" : "inactive"
	List hideStr=schedData?.secData?.hideStr
	String sectStr=schedData?.secData?.schName ? (act ? "Enabled" : "Disabled") : "Tap to Enable"
	String titleStr="Schedule ${schedData?.secData?.scd} (${sectStr})"
	section(title: "${titleStr}                                                            "){//, hideable:schedData?.secData?.hideable, hidden: schedData?.secData?.hidden) {
		input "${sLbl}SchedActive", "bool", title: imgTitle(getAppImg("${actIcon}_icon.png"), inputTitleStr("Schedule Enabled")), description: ( (cnt == 1 && !act) ? "Enable to Edit Schedule" : null), required: true,
				defaultValue: false, submitOnChange: true
		if(act){
			input "${sLbl}name", "text", title: imgTitle(getAppImg("name_tag_icon.png"), inputTitleStr("Schedule Name")), required: true, defaultValue: "Schedule ${cnt}", multiple: false, submitOnChange: true
		}
	}
	if(act){
		section("(${schedData?.secData?.schName ?: "Schedule ${cnt}"}) Setpoint Configuration:                                     ", hideable: true, hidden: (settings["${sLbl}HeatTemp"] != null && settings["${sLbl}CoolTemp"] != null) ){
			paragraph paraTitleStr("Setpoints and Mode")
			paragraph sectionTitleStr("Configure Setpoints and HVAC modes that will be set when this Schedule is in use")
			if(canHeat){
				input "${sLbl}HeatTemp", "decimal", title: imgTitle(getAppImg("heat_icon.png"), inputTitleStr("Heat Set Point (${tempScaleStr})")), description: "Range within ${tempRangeValues()}", required: true, range: tempRangeValues(),
						submitOnChange: true
			}
			if(canCool){
				input "${sLbl}CoolTemp", "decimal", title: imgTitle(getAppImg("cool_icon.png"), inputTitleStr("Cool Set Point (${tempScaleStr})")), description: "Range within ${tempRangeValues()}", required: true, range: tempRangeValues(),
						submitOnChange: true
			}
			input "${sLbl}HvacMode", "enum", title: imgTitle(getAppImg("i_hmod"), inputTitleStr("Set Hvac Mode (Optional):")), required: false, description: "No change set", options: tModeHvacEnum(canHeat,canCool, true), multiple: false
		}
		if(settings.schMotRemoteSensor && !("remSen" in hideStr)){
			section("(${schedData?.secData?.schName ?: "Schedule ${cnt}"}) Remote Sensor Options:                                           ", hideable: true, hidden: (settings["${sLbl}remSensor"] == null && settings["${sLbl}remSenThreshold"] == null)){
				paragraph paraTitleStr("Alternate Remote Sensors\n(Optional)")
				paragraph sectionTitleStr("Configure alternate Remote Temp sensors that are active with this schedule")
				input "${sLbl}remSensor", "capability.temperatureMeasurement", title: imgTitle(getAppImg("i_t"), inputTitleStr("Alternate Temp Sensors")), description: "For Remote Sensor Automation", submitOnChange: true, required: false, multiple: true
				if(settings."${sLbl}remSensor" != null){
					String tt0=settings["${sLbl}remSensor"]?.size() > 1 ? " (avg):" : ":"
					def t1=getDeviceTempAvg(settings["${sLbl}remSensor"])
					String tmpVal="Temp${tt0} (${t1} ${tempScaleStr})"
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("${tmpVal}")), state: "complete"
				}

				paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("Alternate Action Threshold Temp\n(Optional)?"))
				paragraph sectionTitleStr("Temp difference to trigger HVAC operations used with this schedule")
				input "${sLbl}remSenThreshold", "decimal", title: imgTitle(getAppImg("temp_icon.png"), inputTitleStr("Action Threshold Temp (${tempScaleStr})")), required: false, defaultValue: 2.0
			}
		}
		section("(${schedData?.secData?.schName ?: "Schedule ${cnt}"}) Motion Sensor Setpoints:                                        ", hideable: true, hidden:(settings["${sLbl}Motion"] == null) ){
			paragraph paraTitleStr("Optional")
			paragraph sectionTitleStr("Activate alternate HVAC settings with Motion")
			def mmot=settings["${sLbl}Motion"]
			input "${sLbl}Motion", "capability.motionSensor", title: imgTitle(getAppImg("motion_icon.png"), inputTitleStr("Motion Sensors")), description: "Select Sensors to Use", required: false, multiple: true, submitOnChange: true
			if(settings["${sLbl}Motion"]){
				paragraph imgTitle(getAppImg("i_inst"), paraTitleStr(" • Motion State: (${isMotionActive(mmot) ? "Active" : "Not Active"})")), state: "complete"
				if(canHeat){
					input "${sLbl}MHeatTemp", "decimal", title: imgTitle(getAppImg("heat_icon.png"), inputTitleStr("Heat Setpoint with Motion(${tempScaleStr})")), description: "Range within ${tempRangeValues()}", required: true, range: tempRangeValues()
				}
				if(canCool){
					input "${sLbl}MCoolTemp", "decimal", title: imgTitle(getAppImg("cool_icon.png"), inputTitleStr("Cool Setpoint with Motion (${tempScaleStr})")), description: "Range within ${tempRangeValues()}", required: true, range: tempRangeValues()
				}
				input "${sLbl}MHvacMode", "enum", title: imgTitle(getAppImg("i_hmod"), inputTitleStr("Set Hvac Mode with Motion:")), required: false, description: "No change set", options: tModeHvacEnum(canHeat,canCool,true), multiple: false
//				input "${sLbl}MRestrictionMode", "mode", title: "Ignore in these modes", description: "Any location mode", required: false, multiple: true, image: getAppImg("i_mod")
//				input "${sLbl}MPresHome", "capability.presenceSensor", title: "Only act when these people are home", description: "Always", required: false, multiple: true, image: getAppImg("nest_dev_pres_icon.png")
//				input "${sLbl}MPresAway", "capability.presenceSensor", title: "Only act when these people are away", description: "Always", required: false, multiple: true, image: getAppImg("nest_dev_away_icon.png")
				input "${sLbl}MDelayValOn", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Delay Motion Setting Changes")), required: false, defaultValue: 60, options: longTimeSecEnum(), multiple: false
				input "${sLbl}MDelayValOff", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Delay disabling Motion Settings")), required: false, defaultValue: 1800, options: longTimeSecEnum(), multiple: false
			}
		}

		String timeFrom=settings["${sLbl}rstrctTimeFrom"]
		String timeTo=settings["${sLbl}rstrctTimeTo"]
		Boolean showTime=(timeFrom || timeTo || settings."${sLbl}rstrctTimeFromCustom" || settings."${sLbl}rstrctTimeToCustom") ? true : false
		Boolean myShow=!(settings["${sLbl}rstrctMode"] || settings["${sLbl}restrictionDOW"] || showTime || settings["${sLbl}rstrctSWOn"] || settings["${sLbl}rstrctSWOff"] || settings["${sLbl}rstrctPHome"] || settings["${sLbl}rstrctPAway"] )
		section("(${schedData?.secData?.schName ?: "Schedule ${cnt}"}) Schedule Restrictions:                                          ", hideable: true, hidden: myShow){
			paragraph paraTitleStr("Optional")
			paragraph sectionTitleStr("Restrict when this Schedule is in use")
			input "${sLbl}rstrctMode", "mode", title: imgTitle(getAppImg("i_mod"), inputTitleStr("Only execute in these modes")), description: "Any location mode", required: false, multiple: true
			input "${sLbl}restrictionDOW", "enum", options: timeDayOfWeekOptions(), title: imgTitle(getAppImg("day_calendar_icon2.png"), inputTitleStr("Only execute on these days")), description: "Any week day", required: false, multiple: true
			input "${sLbl}rstrctTimeFrom", "enum", title: imgTitle(getAppImg("start_time_icon.png"), inputTitleStr((timeFrom ? "Only execute if time is between" : "Only execute during this time"))), options: timeComparisonOptionValues(), required: showTime, multiple: false, submitOnChange: true
			if(showTime){
				if((timeFrom && timeFrom.contains("custom")) || settings."${sLbl}rstrctTimeFromCustom" != null){
					input "${sLbl}rstrctTimeFromCustom", "time", title: inputTitleStr("Custom time"), required: true, multiple: false
				}else{
					input "${sLbl}rstrctTimeFromOffset", "number", title: imgTitle(getAppImg("offset_icon.png"), inputTitleStr("Offset (+/- minutes)")), range: "*..*", required: true, multiple: false, defaultValue: 0
				}
				input "${sLbl}rstrctTimeTo", "enum", title: imgTitle(getAppImg("stop_time_icon.png"), inputTitleStr("And")), options: timeComparisonOptionValues(), required: true, multiple: false, submitOnChange: true
				if((timeTo && timeTo.contains("custom")) || settings."${sLbl}rstrctTimeToCustom" != null){
					input "${sLbl}rstrctTimeToCustom", "time", title: inputTitleStr("Custom time"), required: true, multiple: false
				}else{
					input "${sLbl}rstrctTimeToOffset", "number", title: imgTitle(getAppImg("offset_icon.png"), inputTitleStr("Offset (+/- minutes)")), range: "*..*", required: true, multiple: false, defaultValue: 0
				}
			}
			input "${sLbl}rstrctPHome", "capability.presenceSensor", title: imgTitle(getAppImg("nest_dev_pres_icon.png"), inputTitleStr("Only execute when one or more of these People are home")), description: "Always", required: false, multiple: true
			input "${sLbl}rstrctPAway", "capability.presenceSensor", title: imgTitle(getAppImg("nest_dev_away_icon.png"), inputTitleStr("Only execute when all these People are away")), description: "Always", required: false, multiple: true
			input "${sLbl}rstrctSWOn", "capability.switch", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Only execute when these switches are all on")), description: "Always", required: false, multiple: true
			input "${sLbl}rstrctSWOff", "capability.switch", title: imgTitle(getAppImg("switch_off_icon.png"), inputTitleStr("Only execute when these switches are all off")), description: "Always", required: false, multiple: true
		}
	}
}

Map getScheduleDesc(Integer num=null){
	Map result=[:]
	Map schedData=(Map)state.activeSchedData
	Integer actSchedNum=getCurrentSchedule()
	String tempScaleStr=tUnitStr()
	Integer schNum
	Map schData

	Integer sCnt=1
	def sData=schedData
	if(num){
		sData=schedData?.find { it?.key.toInteger() == num.toInteger() }
	}
	if(sData?.size()){
		sData?.sort().each { scd ->
			String str=""
			schNum=scd?.key
			schData=scd?.value
			String sLbl="schMot_${schNum}_"
			Boolean isRestrict=(schData?.m || schData?.tf || schData?.tfc || schData?.tfo || schData?.tt || schData?.ttc || schData?.tto || schData?.w || schData?.s1 || schData?.s0 || schData?.p1 || schData?.p0)
			Boolean isTimeRes=(schData?.tf || schData?.tfc || schData?.tfo || schData?.tt || schData?.ttc || schData?.tto)
			Boolean isDayRes=schData?.w
			Boolean isTemp=(schData?.ctemp || schData?.htemp || schData?.hvacm)
			Boolean isSw=(schData?.s1 || schData?.s0)
			Boolean isPres=(schData?.p1 || schData?.p0)
			Boolean isMot=schData?.m0
			Boolean isRemSen=(schData?.sen0 || schData?.thres)
			Boolean isFanEn=schData?.fan0
			String resPreBar=isSw || isPres || isTemp ? "│" : " "
			String tempPreBar=isMot || isRemSen ? "│" : "   "
			Boolean motPreBar=isRemSen

			str += schData?.lbl ? " • ${schData?.lbl}${(actSchedNum?.toInteger() == schNum?.toInteger()) ? " (In Use)" : " (Not In Use)"}" : ""

			//restriction section
			str += isRestrict ? "\n ${isSw || isPres || isTemp ? "├" : "└"} Restrictions:" : ""
			Integer mLen=schData?.m ? schData?.m?.toString().length() : 0
			String mStr=""
			Integer mdSize=1
			schData?.m?.each { md ->
				mStr += md ? "\n ${isSw || isPres || isTemp ? "│ ${(isDayRes || isTimeRes || isPres || isSw) ? "│" : "    "}" : "   "} ${mdSize < schData?.m.size() ? "├" : "└"} ${md.toString()}" : ""
				mdSize=mdSize+1
			}
			str += schData?.m ? "\n ${resPreBar} ${(isTimeRes || schData?.w) ? "├" : "└"} Mode${schData?.m?.size() > 1 ? "s" : ""}:${isInMode(schData?.m) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			str += schData?.m ? "$mStr" : ""

			String dayStr=getAbrevDay(schData?.w)
			String timeDesc=getScheduleTimeDesc(schData?.tf, schData?.tfc, (Integer)schData?.tfo, schData?.tt, schData?.ttc, (Integer)schData?.tto, (isSw || isPres || isDayRes))
			str += isTimeRes ?	"\n │ ${isDayRes || isPres || isSw ? "├" : "└"} ${timeDesc}" : ""
			str += isDayRes ?	"\n │ ${isSw || isPres ? "├" : "└"} Days:${getSchRestrictDoWOk(schNum) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			str += isDayRes ?	"\n │ ${isSw || isPres ? "│" :"    "} └ ${dayStr}" : ""

			// def p1Len=schData?.p1 ? schData?.p1?.toString().length() : 0
			// def p1Str=""
			// def p1dSize=1
			// settings["${sLbl}rstrctPAway"]?.each { ps1 ->
			// 	p1Str += ps1 ? "\n ${isSw || isPres || isTemp ? "│     " : "     "} ${p1dSize < settings["${sLbl}rstrctPAway"].size() ? "├" : "└"} ${ps1.toString()}${!isPresenceHome(ps1) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			// 	p1dSize=p1dSize+1
			// }
			// def p0Len=schData?.p0 ? schData?.p0?.toString().length() : 0
			// def p0Str=""
			// def p0dSize=1
			// settings["${sLbl}rstrctPHome"]?.each { ps0 ->
			// 	p0Str += ps0 ? "\n ${isSw || isPres || isTemp ? "│     " : "     "} ${p0dSize < settings["${sLbl}rstrctPHome"].size() ? "├" : "└"} ${ps0.toString()}" : ""
			// 	p0dSize=p0dSize+1
			// }
			str += schData?.p1 ?	"\n │ ${(schData?.p0 || isSw) ? "├" : "└"} Presence Home:${isSomebodyHome(settings["${sLbl}rstrctPHome"]) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			//str += schData?.p1 ? "$p1Str" : ""
			str += schData?.p1 ?	"\n │ ${(schData?.p0 || isSw) ? "│" : "   "} └ (${schData?.p1.size()} Selected)" : ""
			str += schData?.p0 ?	"\n │ ${isSw ? "├" : "└"} Presence Away:${!isSomebodyHome(settings["${sLbl}rstrctPAway"]) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			//str += schData?.p0 ? "$p0Str" : ""
			str += schData?.p0 ?	"\n │ ${isSw ? "│" : "   "} └ (${schData?.p0.size()} Selected)" : ""

			str += schData?.s1 ?	"\n │ ${schData?.s0 ? "├" : "└"} Switches On:${isSwitchOn(settings["${sLbl}rstrctSWOn"]) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			str += schData?.s1 ?	"\n │ ${schData?.s0 ? "│" : "   "} └ (${schData?.s1.size()} Selected)" : ""
			str += schData?.s0 ?	"\n │ └ Switches Off:${!isSwitchOn(settings["${sLbl}rstrctSWOff"]) ? " (${okSym()})" : " (${notOkSym()})"}" : ""
			str += schData?.s0 ?	"\n │      └ (${schData?.s0.size()} Selected)" : ""

			//Temp Setpoints
			str += isTemp  ?	"${isRestrict ? "\n │\n" : "\n"} ${(isMot || isRemSen) ? "├" : "└"} Temp Setpoints:" : ""
			str += schData?.ctemp ? "\n ${tempPreBar}  ${schData?.htemp ? "├" : "└"} Cool Setpoint: (${fixTempSetting(schData?.ctemp)}${tempScaleStr})" : ""
			str += schData?.htemp ? "\n ${tempPreBar}  ${schData?.hvacm ? "├" : "└"} Heat Setpoint: (${fixTempSetting(schData?.htemp)}${tempScaleStr})" : ""
			str += schData?.hvacm ? "\n ${tempPreBar}  └ HVAC Mode: (${strCapitalize(schData?.hvacm)})" : ""

			//Motion Info
			// def m0Len=schData?.p0 ? schData?.p0?.toString().length() : 0
			// def m0Str=""
			// def m0dSize=1
			// schData?.m0?.each { ms0 ->
			// 	m0Str += ms0 ? "\n     ${isTemp || isFanEn || isRemSen || isRestrict ? "│" : " "} ${m0dSize < schData?.m0.size() ? "├" : "└"} ${ms0.toString()}" : ""
			// 	m0dSize=m0dSize+1
			// }
			str += isMot ?				"${isTemp || isFanEn || isRemSen || isRestrict ? "\n │\n" : "\n"} ${isRemSen ? "├" : "└"} Motion Settings:" : ""
			str += isMot ?				"\n ${motPreBar ? "│" : "   "} ${(schData?.mctemp || schData?.mhtemp) ? "├" : "└"} Motion Sensors: (${schData?.m0.size()})" : ""
			//str += schData?.m0 ? "$m0Str" : ""
			//str += isMot ?				"\n ${motPreBar ? "│" : "   "} ${schData?.mctemp || schData?.mhtemp ? "│" : ""} └ (${isMotionActive(settings["${sLbl}Motion"]) ? "Active" : "None Active"})" : ""
			str += isMot && schData?.mctemp ?	"\n ${motPreBar ? "│" : "   "} ${(schData?.mctemp || schData?.mhtemp) ? "├" : "└"} Mot. Cool Setpoint: (${fixTempSetting(schData?.mctemp)}${tempScaleStr})" : ""
			str += isMot && schData?.mhtemp ?	"\n ${motPreBar ? "│" : "   "} ${schData?.mdelayOn || schData?.mdelayOff ? "├" : "└"} Mot. Heat Setpoint: (${fixTempSetting(schData?.mhtemp)}${tempScaleStr})" : ""
			str += isMot && schData?.mhvacm ?	"\n ${motPreBar ? "│" : "   "} ${(schData?.mdelayOn || schData?.mdelayOff) ? "├" : "└"} Mot. HVAC Mode: (${strCapitalize(schData?.mhvacm)})" : ""
			str += isMot && schData?.mdelayOn ?	"\n ${motPreBar ? "│" : "   "} ${schData?.mdelayOff ? "├" : "└"} Mot. On Delay: (${getEnumValue(longTimeSecEnum(), schData?.mdelayOn)})" : ""
			str += isMot && schData?.mdelayOff ?	"\n ${motPreBar ? "│" : "   "} └ Mot. Off Delay: (${getEnumValue(longTimeSecEnum(), schData?.mdelayOff)})" : ""

			//Remote Sensor Info
			str += isRemSen && schData?.sen0 ?	"${isRemSen || isRestrict ? "\n │\n" : "\n"} └ Alternate Remote Sensor:" : ""
			//str += isRemSen && schData?.sen0 ?	"\n      ├ Temp Sensors: (${schData?.sen0.size()})" : ""
			settings["${sLbl}remSensor"]?.each { t ->
				str += "\n      ├ ${t?.label}: ${(t?.label?.toString()?.length() > 10) ? "\n      │ └ " : ""}(${getDeviceTemp(t)}${tempScaleStr})"
			}
			str += isRemSen && schData?.sen0 ?	"\n      └ Temp${(settings["${sLbl}remSensor"]?.size() > 1) ? " (avg):" : ":"} (${getDeviceTempAvg(settings["${sLbl}remSensor"])}${tempScaleStr})" : ""
			str += isRemSen && schData?.thres ?	"\n  └ Threshold: (${settings["${sLbl}remSenThreshold"]}${tempScaleStr})" : ""
			//log.debug "str: \n$str"
			if(str != ""){ result[schNum]=str.toString() }
		}
	}
	return (result?.size() >= 1) ? result : null
}

String getScheduleTimeDesc(String timeFrom, String timeFromCustom, Integer timeFromOffset, String timeTo, String timeToCustom, Integer timeToOffset, showPreLine=false){
	def tf=new SimpleDateFormat("h:mm a")
		tf.setTimeZone(location?.timeZone)
	String spl=showPreLine == true ? "│" : ""
	String timeToVal=null
	String timeFromVal=null
	Integer i=0
	if(timeFrom && timeTo){
		while (i < 2){
			switch(i == 0 ? timeFrom : timeTo){
				case "custom time":
					if(i == 0){ timeFromVal=tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", timeFromCustom)) }
					else { timeToVal=tf.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", timeToCustom)) }
					break
				case "sunrise":
					def sunTime=((timeFromOffset > 0 || timeToOffset > 0) ? getSunriseAndSunset(zipCode: location.zipCode, sunriseOffset: "00:${i == 0 ? timeFromOffset : timeToOffset}") : getSunriseAndSunset(zipCode: location.zipCode))
					if(i == 0){ timeFromVal="Sunrise: (" + tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", sunTime?.sunrise.toString())) + ")" }
					else { timeToVal="Sunrise: (" + tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", sunTime?.sunrise.toString())) + ")" }
					break
				case "sunset":
					def sunTime=((timeFromOffset > 0 || timeToOffset > 0) ? getSunriseAndSunset(zipCode: location.zipCode, sunriseOffset: "00:${i == 0 ? timeFromOffset : timeToOffset}") : getSunriseAndSunset(zipCode: location.zipCode))
					if(i == 0){ timeFromVal="Sunset: (" + tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", sunTime?.sunset.toString())) + ")" }
					else { timeToVal="Sunset: (" + tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", sunTime?.sunset.toString())) + ")" }
					break
				case "noon":
					Long rightNow=adjustTime().time
					def offSet=(timeFromOffset != null || timeToOffset != null) ? (i == 0 ? (timeFromOffset * 60 * 1000) : (timeToOffset * 60 * 1000)) : 0
					String res="Noon: " + formatTime(convertDateToUnixTime((rightNow - rightNow.mod(86400000) + 43200000) + offSet))
					if(i == 0){ timeFromVal=res }
					else { timeToVal=res }
					break
				case "midnight":
					Long rightNow=adjustTime().time
					def offSet=(timeFromOffset != null || timeToOffset != null) ? (i == 0 ? (timeFromOffset * 60 * 1000) : (timeToOffset * 60 * 1000)) : 0
					String res="Midnight: " + formatTime(convertDateToUnixTime((rightNow - rightNow.mod(86400000)) + offSet))
					if(i == 0){ timeFromVal=res }
					else { timeToVal=res }
				break
			}
			i += 1
		}
	}
	Boolean timeOk=((timeFrom && (timeFromCustom || timeFromOffset) && timeTo && (timeToCustom || timeToOffset)) && checkTimeCondition(timeFrom, timeFromCustom, timeFromOffset, timeTo, timeToCustom, timeToOffset)) ? true : false
	String out=""
	out += (timeFromVal && timeToVal) ? "Time:${timeOk ? " (${okSym()})" : " (${notOkSym()})"}\n │ ${spl}     ├ $timeFromVal\n │ ${spl}     ├   to\n │ ${spl}     └ $timeToVal" : ""
	return out
}

/*
void updSchedActiveState(Integer schNum, String active){
	LogTrace("updSchedActiveState(schNum: $schNum, active: $active)")
	if(schNum && active){
		String sLbl="schMot_${schNum}_SchedActive"
		Boolean curAct=settings["${sLbl}"]
		if(curAct.toString() == active.toString()){ return }
		LogAction("updSchedActiveState | Setting Schedule (${schNum} - ${getSchedLbl(schNum)}) Active to ($active)", "info", false)
		settingUpdate("${sLbl}", "${active}")
	}else{ return }
}
*/
static String okSym(){
	return "✓"// ☑"
}
static String notOkSym(){
	return "✘"
}

def getRemSenTempSrc(){
	return state.remoteTempSourceStr ?: null
}

def getAbrevDay(vals){
	def list=[]
	if(vals){
		//log.debug "days: $vals | (${vals?.size()})"
		def len=(vals?.toString().length() < 7) ? 3 : 2
		vals?.each { d ->
			list.push(d?.toString().substring(0, len))
		}
	}
	return list
}

def roundTemp(Double temp){
	if(temp == null){ return null }
	def newtemp
	if( getTemperatureScale() == "C"){
		newtemp=Math.round(temp.round(1) * 2) / 2.0f
	}else{
		if(temp instanceof Integer){
			//log.debug "roundTemp: ($temp) is Integer"
			newTemp=temp.toInteger()
		}
		else if(temp instanceof Double){
			//log.debug "roundTemp: ($temp) is Double"
			newtemp=temp.round(0).toInteger()
		}
		else if(temp instanceof BigDecimal){
			//log.debug "roundTemp: ($temp) is BigDecimal"
			newtemp=temp.toInteger()
		}
	}
	return newtemp
}

void updateScheduleStateMap(){
	if(autoType == "schMot" && isSchMotConfigured()){
		Map actSchedules=[:]
		Integer numAct=0
		getScheduleList()?.each { Integer scdNum ->
			String sLbl="schMot_${scdNum}_"
			Map newScd=[:]
			Boolean schActive=settings["${sLbl}SchedActive"]

			if(schActive){
				actSchedules?."${scdNum}"=[:]
				newScd=cleanUpMap([
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
				actSchedules?."${scdNum}"=newScd
				//LogAction("updateScheduleMap [ ScheduleNum: $scdNum | PrefixLbl: $sLbl | SchedActive: $schActive | NewSchedData: $newScd ]", "info", false)
			}
		}
		state.activeSchedData=actSchedules
	}
}

List deviceInputToList(items){
	List list=[]
	if(items){
		items.sort().each { d ->
			list.push(d.displayName.toString())
		}
		return list
	}
	return null
}

/*
def inputItemsToList(items){
	def list=[]
	if(items){
		items?.each { d ->
			list.push(d)
		}
		return list
	}
	return null
}
*/

Boolean isSchMotConfigured(){
	return (settings.schMotTstat && (
					settings.schMotOperateFan ||
					settings.schMotRemoteSensor ||
					settings.schMotWaterOff ||
					settings.schMotContactOff ||
					settings.schMotHumidityControl ||
					settings.schMotExternalTempOff))
}

Integer getAutoRunSec(){ return !state.autoRunDt ? 100000 : GetTimeDiffSeconds(state.autoRunDt, null, "getAutoRunSec").toInteger() }

void schMotCheck(){
	LogTrace("schMotCheck")
	try {
		if(getIsAutomationDisabled()){ return }
		Integer schWaitVal=settings.schMotWaitVal?.toInteger() ?: 60
		if(schWaitVal > 120){ schWaitVal=120 }
		Integer t0=getAutoRunSec()
		if(t0 < schWaitVal){
			Integer schChkVal=((schWaitVal - t0) < 30) ? 30 : (schWaitVal - t0)
			scheduleAutomationEval(schChkVal)
			LogAction("Too Soon to Evaluate Actions; Re-Evaluation in (${schChkVal} seconds)", "info", false)
			return
		}

		Long execTime=now()
		state.autoRunDt=getDtNow()

		// This order is important
		// turn system on/off, then update schedule mode/temps, then remote sensors, then update fans

		def updatedWeather=false
		if(settings.schMotWaterOff){
			if(isLeakWatConfigured()){ leakWatCheck() }
		}
		if(settings.schMotContactOff){
			if(isConWatConfigured()){ conWatCheck() }
		}
		if(settings.schMotExternalTempOff){
			if(isExtTmpConfigured()){
				if(settings.extTmpUseWeather && !updatedWeather){ updatedWeather=true; getExtConditions() }
				extTmpTempCheck()
			}
		}
//		if(settings.schMotSetTstatTemp){
			if(isTstatSchedConfigured()){ setTstatTempCheck() }
//		}
		if(settings.schMotRemoteSensor){
			if(isRemSenConfigured()){
				remSenCheck()
			}
		}
		if(settings.schMotHumidityControl){
			if(isHumCtrlConfigured()){
				if(settings.humCtrlUseWeather && !updateWeather){ getExtConditions() }
				humCtrlCheck()
			}
		}
		if(settings.schMotOperateFan){
			if(isFanCtrlConfigured()){
				fanCtrlCheck()
			}
		}

		storeExecutionHistory((now() - execTime), "schMotCheck")
	} catch (ex){
		log.error "schMotCheck Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "schMotCheck", true, getAutoType())
	}
}

void storeLastEventData(evt){
	if(evt){
		Map newVal=["name":evt.name, "displayName":evt.displayName, "value":evt.value, "date":formatDt(evt.date), "unit":evt.unit]
		state.lastEventData=newVal
		//log.debug "LastEvent: ${state.lastEventData}"

		List list=state.detailEventHistory ?: []
		Integer listSize=15
		if(list?.size() < listSize){
			list.push(newVal)
		}
		else if(list?.size() > listSize){
			Integer nSz=(list?.size()-listSize) + 1
			List nList=list?.drop(nSz)
			nList.push(newVal)
			list=nList
		}
		else if(list?.size() == listSize){
			List nList=list?.drop(1)
			nList?.push(newVal)
			list=nList
		}
		if(list){ state.detailEventHistory=list }
	}
}

void storeExecutionHistory(val, String method=(String)null){
	//log.debug "storeExecutionHistory($val, $method)"
//	try {
		if(method){
			LogTrace("${method} Execution Time: (${val} milliseconds)")
		}
		if(method in ["watchDogCheck", "checkNestMode", "schMotCheck"]){
			state.autoExecMS=val ?: null
			def list=state.evalExecutionHistory ?: []
			Integer listSize=20
			list=addToList(val, list, listSize)
			if(list){ state.evalExecutionHistory=list }
		}
		if(!(method in ["watchDogCheck", "checkNestMode"])){
			def list=state.detailExecutionHistory ?: []
			Integer listSize=30
			list=addToList([val, method, getDtNow()], list, listSize)
			if(list){ state.detailExecutionHistory=list }
		}
//	} catch (ex){
//		log.error "storeExecutionHistory Exception:", ex
		//parent?.sendExceptionData(ex, "storeExecutionHistory", true, getAutoType())
//	}
}

static List addToList(val, List list, Integer listSize){
	if(list?.size() < listSize){
		list.push(val)
	}else if(list?.size() > listSize){
		Integer nSz=(list?.size()-listSize) + 1
		List nList=list?.drop(nSz)
		nList?.push(val)
		list=nList
	}else if(list?.size() == listSize){
		List nList=list?.drop(1)
		nList?.push(val)
		list=nList
	}
	return list
}

static Integer getAverageValue(items){
	List tmpAvg=[]
	def val=0
	if(!items){ return val }
	else if(items?.size() > 1){
		tmpAvg=items
		if(tmpAvg){ val=(tmpAvg.sum().toDouble() / tmpAvg.size().toDouble()).round(0) }
	}else{ val=items }
	return val.toInteger()
}

/************************************************************************************************
|								DYNAMIC NOTIFICATION PAGES								|
*************************************************************************************************/

def setNotificationPage1(params){
	//href "setNotificationPage1", title: titles("t_nt"), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null), image: getAppImg("i_not")
	LogTrace("setNotificationPage1()")
	String pName=watchDogPrefix()
	def t0=["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage2(params){
	//href "setNotificationPage2", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":false, "allowAlarm":false, "showSchedule":true], state: (pageDesc ? "complete" : null)
	LogTrace("setNotificationPage2()")
	String pName=nModePrefix()
	def t0=["pName":"${pName}", "allowSpeech":false, "allowAlarm":false, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage3(params){
	//href "setNotificationPage3", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	String pName=leakWatPrefix()
	LogTrace("setNotificationPage3()")
	def t0=["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage4(params){
	//href "setNotificationPage4, title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	String pName=conWatPrefix()
	LogTrace("setNotificationPage4()")
	def t0=["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage5(params){
	//href "setNotificationPage5", title: imgTitle(getAppImg("i_not"),inputTitleStr(titles("t_nt"))), description: pageDesc, params: ["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true], state: (pageDesc ? "complete" : null)
	String pName=extTmpPrefix()
	LogTrace("setNotificationPage5()")
	def t0=["pName":"${pName}", "allowSpeech":true, "allowAlarm":true, "showSchedule":true]
	return setNotificationPage( t0 )
}

def setNotificationPage(params){
	String pName=params?.pName
	Boolean allowSpeech=false
	Boolean allowAlarm=false
	Boolean showSched=false
	if(params?.pName){
		state.t_notifD=params
		allowSpeech=params?.allowSpeech?.toBoolean(); showSched=params?.showSchedule?.toBoolean(); allowAlarm=params?.allowAlarm?.toBoolean()
	}else{
		pName=state.t_notifD?.pName; allowSpeech=state.t_notifD?.allowSpeech; showSched=state.t_notifD?.showSchedule; allowAlarm=state.t_notifD?.allowAlarm
	}
	if(pName == null){ return }
	dynamicPage(name: "setNotificationPage", title: "Configure Notification Options", uninstall: false){
		section(""){
		//section("Notification Preferences:"){
			input "${pName}NotifOn", "bool", title: imgTitle(getAppImg("i_not"), inputTitleStr("Enable Notifications?")), description: (!settings["${pName}NotifOn"] ? "Enable Text, Voice, or Alarm Notifications" : ""), required: false, defaultValue: false, submitOnChange: true
			Boolean fixSettings=false
			if(settings["${pName}NotifOn"]){
//				section("Use NST Manager Settings:"){
					input "${pName}UseMgrNotif", "bool", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Use Manager Settings?")), defaultValue: true, submitOnChange: true, required: false
//				}
				if(settings."${pName}UseMgrNotif" == false){
				settingRemove("${pName}NotifPhones")
		//			section("Enable Text Messaging:"){
		//				input "${pName}NotifPhones", "phone", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Send SMS to Number (Optional)")), required: false, submitOnChange: true
		//			}
		//			section("Enable Pushover Support:"){
						input "${pName}PushoverEnabled", "bool", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
						if(settings."${pName}PushoverEnabled" == true){
							input "${pName}PushoverDevices", "capability.notification", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
						}
		//			}
				}else{
					fixSettings=true
				}
			}else{
				fixSettings=true
			}
			if(fixSettings){
				settingRemove("${pName}NotifPhones")
				settingRemove("${pName}PushoverEnabled")
				settingRemove("${pName}PushoverDevices")
				//settingRemove("${pName}UseParentNotifRestrictions")
			}
/*
			if(allowSpeech && settings."${pName}NotifOn"){
//			section("Voice Notification Preferences:"){
				input "${pName}AllowSpeechNotif", "bool", title: "Enable Voice Notifications?", description: "Media players, or Speech Devices", required: false, defaultValue: (settings."${pName}AllowSpeechNotif" ? true : false), submitOnChange: true, image: getAppImg("speech_icon.png")
				if(settings["${pName}AllowSpeechNotif"]){
					setInitialVoiceMsgs(pName)
					input "${pName}SendToAskAlexaQueue", "bool", title: "Send to Ask Alexa Message Queue?", required: false, defaultValue: (settings."${pName}AllowSpeechNotif" ? false : true), submitOnChange: true,
							image: askAlexaImgUrl()
					input "${pName}SpeechMediaPlayer", "capability.musicPlayer", title: "Select Media Player(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg("media_player.png")
					input "${pName}EchoDevices", "device.echoSpeaksDevice", title: "Select Alexa Devices(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg('echo_speaks.png')
					input "${pName}SpeechDevices", "capability.speechSynthesis", title: "Select Speech Synthesizer(s)", hideWhenEmpty: true, multiple: true, required: false, submitOnChange: true, image: getAppImg("speech2_icon.png")
					if(settings["${pName}SpeechMediaPlayer"] || settings["${pName}EchoDevices"]){
						input "${pName}SpeechVolumeLevel", "number", title: "Default Volume Level?", required: false, defaultValue: 30, range: "0::100", submitOnChange: true, image: getAppImg("volume_icon.png")
						if(settings["${pName}SpeechMediaPlayer"]){
							input "${pName}SpeechAllowResume", "bool", title: "Can Resume Playing Media?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("resume_icon.png")
						}
					}
					def desc=""
					if(pName in ["conWat", "extTmp", "leakWat"]){
						if( (settings["${pName}SpeechMediaPlayer"] || settings["${pName}SpeechDevices"] || settings["${pName}EchoDevices"] || settings["${pName}SendToAskAlexaQueue"]) ){
							switch(pName){
								case "conWat":
									desc="Contact Close"
									break
								case "extTmp":
									desc="External Temperature Threshold"
									break
								case "leakWat":
									desc="Water Dried"
									break
							}

							input "${pName}SpeechOnRestore", "bool", title: "Speak when restoring HVAC on (${desc})?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("speech_icon.png")
							// TODO: There are more messages and errors than ON / OFF
							input "${pName}UseCustomSpeechNotifMsg", "bool", title: "Customize Notitification Message?", required: false, defaultValue: (settings."${pName}AllowSpeechNotif" ? false : true), submitOnChange: true,
								image: getAppImg("speech_icon.png")
							if(settings["${pName}UseCustomSpeechNotifMsg"]){
								getNotifVariables(pName)
								input "${pName}CustomOffSpeechMessage", "text", title: "Turn Off Message?", required: false, defaultValue: state."${pName}OffVoiceMsg" , submitOnChange: true, image: getAppImg("speech_icon.png")
								state."${pName}OffVoiceMsg"=settings."${pName}CustomOffSpeechMessage"
								if(settings."${pName}CustomOffSpeechMessage"){
									paragraph "Off Msg:\n" + voiceNotifString(state."${pName}OffVoiceMsg",pName)
								}
								input "${pName}CustomOnSpeechMessage", "text", title: "Restore On Message?", required: false, defaultValue: state."${pName}OnVoiceMsg", submitOnChange: true, image: getAppImg("speech_icon.png")
								state."${pName}OnVoiceMsg"=settings."${pName}CustomOnSpeechMessage"
								if(settings."${pName}CustomOnSpeechMessage"){
									paragraph "Restore On Msg:\n" + voiceNotifString(state."${pName}OnVoiceMsg",pName)
								}
							}else{
								state."${pName}OffVoiceMsg"=""
								state."${pName}OnVoiceMsg"=""
							}
						}
					}
				}
			//}
		}
*/
			if(allowAlarm && settings."${pName}NotifOn"){
	//			section("Alarm/Siren Device Preferences:"){
					input "${pName}AllowAlarmNotif", "bool", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Enable Alarm | Siren?")), required: false, defaultValue: (settings."${pName}AllowAlarmNotif" ? true : false), submitOnChange: true
					if(settings["${pName}AllowAlarmNotif"]){
						input "${pName}AlarmDevices", "capability.alarm", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Select Alarm/Siren(s)")), multiple: true, required: settings["${pName}AllowAlarmNotif"], submitOnChange: true
					}
	//			}
			}
			if(pName in ["conWat", "leakWat", "extTmp", "watchDog"] && settings["${pName}NotifOn"] && settings["${pName}AllowAlarmNotif"] && settings["${pName}AlarmDevices"]){
	//			section("Notification Alert Options (1):"){
					input "${pName}_Alert_1_Delay", "enum", title: imgTitle(getAppImg("alert_icon2.png"), inputTitleStr("First Alert Delay (in minutes)")), defaultValue: null, required: true, submitOnChange: true, options: longTimeSecEnum()
					if(settings."${pName}_Alert_1_Delay"){
						input "${pName}_Alert_1_AlarmType", "enum", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Alarm Type to use?")), options: alarmActionsEnum(), defaultValue: null, submitOnChange: true, required: true
						if(settings."${pName}_Alert_1_AlarmType"){
							input "${pName}_Alert_1_Alarm_Runtime", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Turn off Alarm After (in seconds)?")), options: shortTimeEnum(), defaultValue: 10, required: true, submitOnChange: true
						}
					}
	//			}
				if(settings["${pName}_Alert_1_Delay"]){
	//				section("Notification Alert Options (2):"){
						input "${pName}_Alert_2_Delay", "enum", title: imgTitle(getAppImg("alert_icon2.png"), inputTitleStr("Second Alert Delay (in minutes)")), defaultValue: null, options: longTimeSecEnum(), required: false, submitOnChange: true
						if(settings."${pName}_Alert_2_Delay"){
							input "${pName}_Alert_2_AlarmType", "enum", title: imgTitle(getAppImg("alarm_icon.png"), inputTitleStr("Alarm Type to use?")), options: alarmActionsEnum(), defaultValue: null, submitOnChange: true, required: true
							if(settings."${pName}_Alert_2_AlarmType"){
								input "${pName}_Alert_2_Alarm_Runtime", "enum", title: imgTitle(getAppImg("i_dt"), inputTitleStr("Turn off Alarm After (in minutes)?")), options: shortTimeEnum(), defaultValue: 10, required: true, submitOnChange: true
							}
						}
	//				}
				}
			}
		}
	}
}

/*
def setInitialVoiceMsgs(pName){
	if(settings["${pName}AllowSpeechNotif"]){
		if(pName in ["conWat", "extTmp", "leakWat"]){
			if(pName == "leakWat"){
				if(!state."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OffVoiceMsg"="ATTENTION: %devicename% has been turned OFF because %wetsensor% has reported it is WET" }
				if(!state."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OnVoiceMsg"="Restoring %devicename% to %lastmode% Mode because ALL water sensors have been Dry again for (%ondelay%)" }
			}
			if(pName == "conWat"){
				if(!state."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OffVoiceMsg"="ATTENTION: %devicename% has been turned OFF because %opencontact% has been Opened for (%offdelay%)" }
				if(!state."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OnVoiceMsg"="Restoring %devicename% to %lastmode% Mode because ALL contacts have been Closed again for (%ondelay%)" }
			}
			if(pName == "extTmp"){
				if(!state."${pName}OffVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OffVoiceMsg"="ATTENTION: %devicename% has been turned to ECO because External Temp is above the temp threshold for (%offdelay%)" }
				if(!state."${pName}OnVoiceMsg" || !settings["${pName}UseCustomSpeechNotifMsg"]){
					state."${pName}OnVoiceMsg"="Restoring %devicename% to %lastmode% Mode because External Temp has been above the temp threshold for (%ondelay%)" }
			}
		}
	}
}
*/

//ERS
/*
def setCustomVoice(pName){
	if(settings["${pName}AllowSpeechNotif"]){
		if(pName in ["conWat", "extTmp", "leakWat"]){
			if(settings["${pName}UseCustomSpeechNotifMsg"]){
				state."${pName}OffVoiceMsg"=settings."${pName}CustomOffSpeechMessage"
				state."${pName}OnVoiceMsg"=settings."${pName}CustomOnSpeechMessage"
			}
		}
	}
}
*/

/*
def setNotificationTimePage(params){
	def pName=params?.pName
	if(params?.pName){
		state.curNotifTimePageData=params
	}else{ pName=state.curNotifTimePageData?.pName }
	dynamicPage(name: "setNotificationTimePage", title: "Prevent Notifications\nDuring these Days, Times or Modes", uninstall: false){
		def timeReq=(settings["${pName}qStartTime"] || settings["${pName}qStopTime"]) ? true : false
		section(){
			input "${pName}qStartInput", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("start_time_icon.png")
			if(settings["${pName}qStartInput"] == "A specific time"){
				input "${pName}qStartTime", "time", title: "Start time", required: timeReq, image: getAppImg("start_time_icon.png")
			}
			input "${pName}qStopInput", "enum", title: "Stopping at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("stop_time_icon.png")
			if(settings."${pName}qStopInput" == "A specific time"){
				input "${pName}qStopTime", "time", title: "Stop time", required: timeReq, image: getAppImg("stop_time_icon.png")
			}
			input "${pName}quietDays", "enum", title: "Prevent during these days of the week", multiple: true, required: false, image: getAppImg("day_calendar_icon.png"), options: timeDayOfWeekOptions()
			input "${pName}quietModes", "mode", title: "Prevent when these Modes are Active", multiple: true, submitOnChange: true, required: false, image: getAppImg("i_mod")
		}
	}
}

String getNotifSchedDesc(pName){
	def sun=getSunriseAndSunset()
	def startInput=settings."${pName}qStartInput"
	String startTime=settings."${pName}qStartTime"
	def stopInput=settings."${pName}qStopInput"
	String stopTime=settings."${pName}qStopTime"
	def dayInput=settings."${pName}quietDays"
	def modeInput=settings."${pName}quietModes"
	String notifDesc=""
	if(settings."${pName}UseParentNotifRestrictions" == false){
		def getNotifTimeStartLbl=( (startInput == "Sunrise" || startInput == "Sunset") ? ( (startInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (startTime ? time2Str(startTime) : "") )
		def getNotifTimeStopLbl=( (stopInput == "Sunrise" || stopInput == "Sunset") ? ( (stopInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (stopTime ? time2Str(stopTime) : "") )
		notifDesc += (getNotifTimeStartLbl && getNotifTimeStopLbl) ? "• Silent Time: ${getNotifTimeStartLbl} - ${getNotifTimeStopLbl}" : ""
		def days=getInputToStringDesc(dayInput)
		def modes=getInputToStringDesc(modeInput)
		notifDesc += days ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl) ? "\n" : ""}• Silent Day${isPluralString(dayInput)}: ${days}" : ""
		notifDesc += modes ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl || days) ? "\n" : ""}• Silent Mode${isPluralString(modeInput)}: ${modes}" : ""
	}else{
		notifDesc += "• Using Manager Restrictions"
	}
	return (notifDesc != "") ? "${notifDesc}" : null
}

def getOk2Notify(pName){
	return ((settings["${pName}NotifOn"] == true) && (daysOk(settings."${pName}quietDays") == true) && (notificationTimeOk(pName) == true) && (modesOk(settings."${pName}quietModes") == true))
}

Boolean notificationTimeOk(pName){
	def strtTime=null
	def stopTime=null
	Date now=new Date()
	def sun=getSunriseAndSunset() // current based on geofence, previously was: def sun=getSunriseAndSunset(zipCode: zipCode)
	if(settings."${pName}qStartTime" && settings."${pName}qStopTime"){
		if(settings."${pName}qStartInput" == "sunset"){ strtTime=sun.sunset }
		else if(settings."${pName}qStartInput" == "sunrise"){ strtTime=sun.sunrise }
		else if(settings."${pName}qStartInput" == "A specific time" && settings."${pName}qStartTime"){ strtTime=settings."${pName}qStartTime" }

		if(settings."${pName}qStopInput" == "sunset"){ stopTime=sun.sunset }
		else if(settings."${pName}qStopInput" == "sunrise"){ stopTime=sun.sunrise }
		else if(settings."${pName}qStopInput" == "A specific time" && settings."${pName}qStopTime"){ stopTime=settings."${pName}qStopTime" }
	}else{ return true }
	if(strtTime && stopTime){
		return timeOfDayIsBetween(strtTime, stopTime, new Date(), getTimeZone()) ? false : true
	}else{ return true }
}

def getNotifVariables(pName){
	String str=""
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
def voiceNotifString(phrase, pName){
	//LogTrace("conWatVoiceNotifString")
	try {
		if(phrase?.toLowerCase().contains("%devicename%")){ phrase=phrase?.toLowerCase().replace('%devicename%', (settings."schMotTstat"?.displayName.toString() ?: "unknown")) }
		if(phrase?.toLowerCase().contains("%lastmode%")){ phrase=phrase?.toLowerCase().replace('%lastmode%', (state."${pName}RestoreMode".toString() ?: "unknown")) }
		if(pName == "leakWat" && phrase?.toLowerCase().contains("%wetsensor%")){
			phrase=phrase?.toLowerCase().replace('%wetsensor%', (getWetWaterSensors(leakWatSensors) ? getWetWaterSensors(leakWatSensors)?.join(", ").toString() : "a selected leak sensor")) }
		if(pName == "conWat" && phrase?.toLowerCase().contains("%opencontact%")){
			phrase=phrase?.toLowerCase().replace('%opencontact%', (getOpenContacts(conWatContacts) ? getOpenContacts(conWatContacts)?.join(", ").toString() : "a selected contact")) }
		if(pName == "extTmp" && phrase?.toLowerCase().contains("%tempthreshold%")){
			phrase=phrase?.toLowerCase().replace('%tempthreshold%', "${extTmpDiffVal.toString()}(${tUnitStr()})") }
		if(phrase?.toLowerCase().contains("%offdelay%")){ phrase=phrase?.toLowerCase().replace('%offdelay%', getEnumValue(longTimeSecEnum(), settings."${pName}OffDelay").toString()) }
		if(phrase?.toLowerCase().contains("%ondelay%")){ phrase=phrase?.toLowerCase().replace('%ondelay%', getEnumValue(longTimeSecEnum(), settings."${pName}OnDelay").toString()) }
	} catch (ex){
		log.error "voiceNotifString Exception:", ex
		//parent?.sendExceptionData(ex, "voiceNotifString", true, getAutoType())
	}
	return phrase
}
*/

String getNotifConfigDesc(String pName){
	//LogTrace("getNotifConfigDesc pName: $pName")
	String str=""
	if(settings."${pName}NotifOn"){
		// str += "Notification Status:"
		// if(!getRecipientDesc(pName)){
		// 	str += "\n • Contacts: Using Manager Settings"
		// }
		String t0
		if(settings."${pName}UseMgrNotif" == false){
//			str += (settings."${pName}NotifPhones") ? "${str != "" ? "\n" : ""} • SMS: (${settings."${pName}NotifPhones"?.size()})" : ""
			str += (settings."${pName}PushoverEnabled") ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
			str += (settings."${pName}PushoverEnabled" && settings."${pName}PushoverDevices") ? "${str != "" ? "\n" : ""} • Pushover Devices: (${settings."${pName}PushoverDevices"})" : ""
			//t0=getNotifSchedDesc(pName)
			//str += t0 ? "\n\nAlert Restrictions:\n${t0}" : ""
		}else{
			str += " • Enabled Using Manager Settings"
		}
		t0=str
		if(t0){
			str="Notification Settings\n${t0}"
		}
		//t0=getVoiceNotifConfigDesc(pName)
		//str += t0 ? "\n\nVoice Status:${t0}" : ""
		t0=getAlarmNotifConfigDesc(pName)
		str += t0 ?  "\n\nAlarm Status:${t0}" : ""
		t0=getAlertNotifConfigDesc(pName)
		str += t0 ? "\n\n${t0}" : ""
	}
	return (str != "") ? "${str}" : null
}

/*
def getVoiceNotifConfigDesc(pName){
	String str=""
	if(settings."${pName}NotifOn" && settings["${pName}AllowSpeechNotif"]){
		def speaks=settings."${pName}SpeechDevices"
		def medias=settings."${pName}SpeechMediaPlayer"
		def echos=settings["${pName}EchoDevices"]
		str += settings["${pName}SendToAskAlexaQueue"] ? "\n• Send to Ask Alexa: (True)" : ""
		str += speaks ? "\n • Speech Devices:" : ""
		if(speaks){
			def cnt=1
			speaks?.each { str += it ? "\n ${cnt < speaks.size() ? "├" : "└"} $it" : ""; cnt=cnt+1; }
		}
		str += echos ? "\n • Alexa Devices:" : ""
		if(echos){
			def cnt=1
			echos?.each { str += it ? "\n ${cnt < echos.size() ? "├" : "└"} $it" : ""; cnt=cnt+1; }
			str += (echos && settings."${pName}SpeechVolumeLevel") ? "\n└ Volume: (${settings."${pName}SpeechVolumeLevel"})" : ""
		}
		str += medias ? "${(speaks || echos) ? "\n\n" : "\n"} • Media Players:" : ""
		if(medias){
			def cnt=1
			medias?.sort { it?.displayName }?.each { str += it ? "\n│${cnt < medias.size() ? "├" : "└"} $it" : ""; cnt=cnt+1; }
		}
		str += (medias && settings."${pName}SpeechVolumeLevel") ? "\n├ Volume: (${settings."${pName}SpeechVolumeLevel"})" : ""
		str += (medias && settings."${pName}SpeechAllowResume") ? "\n└ Resume: (${strCapitalize(settings."${pName}SpeechAllowResume")})" : ""
		str += (settings."${pName}UseCustomSpeechNotifMsg" && (medias || speaks)) ? "\n• Custom Message: (${strCapitalize(settings."${pName}UseCustomSpeechNotifMsg")})" : ""
	}
	return (str != "") ? "${str}" : null
}
*/

String getAlarmNotifConfigDesc(pName){
	String str=""
	if(settings."${pName}NotifOn" && settings["${pName}AllowAlarmNotif"]){
		def alarms=getInputToStringDesc(settings["${pName}AlarmDevices"], true)
		str += alarms ? "\n • Alarm Devices:${alarms.size() > 1 ? "\n" : ""}${alarms}" : ""
	}
	return (str != "") ? "${str}" : null
}

String getAlertNotifConfigDesc(pName){
	String str=""
//TODO not sure we do all these
	if(settings."${pName}NotifOn" && (settings["${pName}_Alert_1_Delay"] || settings["${pName}_Alert_2_Delay"]) && (settings["${pName}AllowSpeechNotif"] || settings["${pName}AllowAlarmNotif"])){
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

String getInputToStringDesc(inpt, addSpace=null){
	Integer cnt=0
	String str=""
	if(inpt){
		inpt.sort().each { item ->
			cnt=cnt+1
			str += item ? (((cnt < 1) || (inpt?.size() > 1)) ? "\n    ${item}" : "${addSpace ? "    " : ""}${item}") : ""
		}
	}
	//log.debug "str: $str"
	return (str != "") ? "${str}" : null
}

String isPluralString(obj){
	return (obj?.size() > 1) ? "(s)" : ""
}

/*
def getRecipientsNames(val){
	String n=""
	Integer i=0
	if(val){
		//log.debug "val: $val"
		val?.each { r ->
			i=i + 1
			n += i == val?.size() ? "${r}" : "${r},"
		}
	}
	return n?.toString().replaceAll("\\,", "\n")
}

def getRecipientDesc(pName){
	return (settings."${pName}NotifPhones" || (settings."${pName}PushoverEnabled" && settings."${pName}PushoverDevices")) ? true : false
}
*/

def setDayModeTimePage1(params){
	String mpName=nModePrefix()
	def t0=[pName:mpName ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage2(params){
	String mpName=fanCtrlPrefix()
	def t0=[pName:mpName ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage3(params){
	String mpName=conWatPrefix()
	def t0=[pName:mpName ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage4(params){
	String mpName=humCtrlPrefix()
	def t0=[pName:mpName ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage5(params){
	String mpName=extTmpPrefix()
	def t0=[pName:mpName ]
	return setDayModeTimePage( t0 )
}

def setDayModeTimePage(params){
	String pName=params?.pName
	if(params?.pName){
		state.t_setDayData=params
	}else{
		pName=state.t_setDayData?.pName
	}
	dynamicPage(name: "setDayModeTimePage", title: "Select Days, Times or Modes", uninstall: false){
		String secDesc=settings["${pName}DmtInvert"] ? "Not" : "Only"
		Boolean inverted=settings["${pName}DmtInvert"] ? true : false
		section(""){
			String actIcon=settings."${pName}DmtInvert" ? "inactive" : "active"
			input "${pName}DmtInvert", "bool", title: imgTitle(getAppImg("${actIcon}_icon.png"), inputTitleStr("${secDesc} in These? (tap to invert)")), defaultValue: false, submitOnChange: true
		}
		section("${secDesc} During these Days, Times, or Modes:"){
			Boolean timeReq=(settings."${pName}StartTime" || settings."${pName}StopTime") ? true : false
			input "${pName}StartTime", "time", title: imgTitle(getAppImg("start_time_icon.png"), inputTitleStr("Start time")), required: timeReq
			input "${pName}StopTime", "time", title: imgTitle(getAppImg("stop_time_icon.png"), inputTitleStr("Stop time")), required: timeReq
			input "${pName}Days", "enum", title: imgTitle(getAppImg("day_calendar_icon2.png"), inputTitleStr("${inverted ? "Not": "Only"} These Days")), multiple: true, required: false, options: timeDayOfWeekOptions()
			input "${pName}Modes", "mode", title: imgTitle(getAppImg("i_mod"), inputTitleStr("${inverted ? "Not": "Only"} in These Modes")), multiple: true, required: false
		}
		section("Switches:"){
			input "${pName}rstrctSWOn", "capability.switch", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Only execute when these switches are all ON")), multiple: true, required: false
			input "${pName}rstrctSWOff", "capability.switch", title: imgTitle(getAppImg("switch_off_icon.png"), inputTitleStr("Only execute when these switches are all OFF")), multiple: true, required: false
		}
	}
}

String getDayModeTimeDesc(String pName){
	String startTime=settings."${pName}StartTime"
	String stopTime=settings."${pName}StopTime"
	def dayInput=settings."${pName}Days"
	def modeInput=settings."${pName}Modes"
	Boolean inverted=settings."${pName}DmtInvert" ?: null
	def swOnInput=settings."${pName}rstrctSWOn"
	def swOffInput=settings."${pName}rstrctSWOff"
	String str=""
	String days=getInputToStringDesc(dayInput)
	String modes=getInputToStringDesc(modeInput)
	String swOn=getInputToStringDesc(swOnInput)
	String swOff=getInputToStringDesc(swOffInput)
	str += ((startTime && stopTime) || modes || days) ? "${!inverted ? "When" : "When Not"}:" : ""
	str += (startTime && stopTime) ? "\n • Time: ${time2Str(settings."${pName}StartTime")} - ${time2Str(settings."${pName}StopTime")}" : ""
	str += days ? "${(startTime && stopTime) ? "\n" : ""}\n • Day${isPluralString(dayInput)}: ${days}" : ""
	str += modes ? "${((startTime && stopTime) || days) ? "\n" : ""}\n • Mode${isPluralString(modeInput)}: ${modes}" : ""
	str += swOn ? "${((startTime && stopTime) || days || modes) ? "\n" : ""}\n • Switch${isPluralString(swOnInput)} that must be on: ${getRestSwitch(swOnInput)}" : ""
	str += swOff ? "${((startTime && stopTime) || days || modes || swOn) ? "\n" : ""}\n • Switch${isPluralString(swOffInput)} that must be off: ${getRestSwitch(swOffInput)}" : ""
	str += (str != "") ? descriptions("d_ttm") : ""
	return str
}

String getRestSwitch(swlist){
	String swDesc=""
	Integer swCnt=0
	Integer rmSwCnt=swlist?.size() ?: 0
	swlist?.sort { it?.displayName }?.each { sw ->
		swCnt=swCnt+1
		swDesc += "${swCnt >= 1 ? "${swCnt == rmSwCnt ? "\n   └" : "\n   ├"}" : "\n   └"} ${sw?.label}: (${strCapitalize(sw?.currentSwitch)})"
	}
	return (swDesc == "") ? null : "${swDesc}"
}

String getDmtSectionDesc(String autoType){
	return settings["${autoType}DmtInvert"] ? "Do Not Act During these Days, Times, or Modes:" : "Only Act During these Days, Times, or Modes:"
//TODO add switches to adjust schedule
}

/************************************************************************************************
|				AUTOMATION SCHEDULE CHECK								|
*************************************************************************************************/

Boolean autoScheduleOk(String autoType){
	try {
		Boolean inverted=settings."${autoType}DmtInvert" ? true : false
		Boolean modeOk=true
		modeOk=(!settings."${autoType}Modes" || ((isInMode(settings."${autoType}Modes") && !inverted) || (!isInMode(settings."${autoType}Modes") && inverted))) ? true : false

		//dayOk
		Boolean dayOk=true
		def dayFmt=new SimpleDateFormat("EEEE")
		dayFmt.setTimeZone(getTimeZone())
		String today=dayFmt.format(new Date())
		Boolean inDay=(today in settings."${autoType}Days") ? true : false
		dayOk=(!settings."${autoType}Days" || ((inDay && !inverted) || (!inDay && inverted))) ? true : false

		//scheduleTimeOk
		Boolean timeOk=true
		if(settings."${autoType}StartTime" && settings."${autoType}StopTime"){
			Date st1=timeToday(settings."${autoType}StartTime", getTimeZone())
			Date end1=timeToday(settings."${autoType}StopTime", getTimeZone())
			//def inTime=(timeOfDayIsBetween(settings."${autoType}StartTime", settings."${autoType}StopTime", new Date(), getTimeZone())) ? true : false
			Boolean inTime=timeOfDayIsBetween(st1, end1, new Date(), getTimeZone())
			timeOk=(inTime && !inverted) || (!inTime && inverted)
		}

		Boolean soFarOk=modeOk && dayOk && timeOk
		Boolean swOk=true
		if(soFarOk && settings."${autoType}rstrctSWOn"){
			for(sw in settings["${autoType}rstrctSWOn"]){
				if(sw.currentValue("switch") != "on"){
					swOk=false
					break
				}
			}
		}
		soFarOk=(modeOk && dayOk && timeOk && swOk) ? true : false
		if(soFarOk && settings."${autoType}rstrctSWOff"){
			for(sw in settings["${autoType}rstrctSWOff"]){
				if(sw.currentValue("switch") != "off"){
					swOk=false
					break
				}
			}
		}

		LogAction("autoScheduleOk( dayOk: $dayOk | modeOk: $modeOk | dayOk: ${dayOk} | timeOk: $timeOk | swOk: $swOk | inverted: ${inverted})", "info", false)
		return (modeOk && dayOk && timeOk && swOk) ? true : false
	} catch (ex){
		log.error "${autoType}-autoScheduleOk Exception: ${ex?.message}"
		//parent?.sendExceptionData(ex, "autoScheduleOk", true, getAutoType())
	}
}

/************************************************************************************************
|						SEND NOTIFICATIONS VIA PARENT APP								|
*************************************************************************************************/
void sendNofificationMsg(String msg, String msgType, String pName, lvl=null, pusho=null, sms=null){
	LogAction("sendNofificationMsg($msg, $msgType, $pName, $sms, $pusho)", "debug", false)
	if(settings."${pName}NotifOn" == true){
		Integer nlvl=lvl ?: (sms || pusho) ? 5 : 4
		if(settings."${pName}UseMgrNotif" == false){
//			def mySms=sms ?: settings."${pName}NotifPhones"
//			if(mySms){
//				parent.sendMsg(msgType, msg, nlvl,  null, mySms)
//			}
			if(pusho && settings."${pName}PushoverDevices"){
				parent.sendMsg(msgType, msg, nlvl, settings."${pName}PushoverDevices")
			}
		}else{
			parent.sendMsg(msgType, msg, nlvl)
		}
	}else{
		LogAction("sendMsg: Message Skipped as notifications off ($msg)", "info", true)
	}
}

/************************************************************************************************
|							GLOBAL Code | Logging AND Diagnostic							|
*************************************************************************************************/
void sendEventPushNotifications(String message, String type, String pName){
	LogTrace("sendEventPushNotifications($message, $type, $pName)")
	sendNofificationMsg(message, type, pName)
}

/*
def sendEventVoiceNotifications(vMsg, pName, msgId, rmAAMsg=false, rmMsgId){
	def allowNotif=settings."${pName}NotifOn" ? true : false
	def allowSpeech=allowNotif && settings."${pName}AllowSpeechNotif" ? true : false
	def ok2Notify=setting?."${pName}UseParentNotifRestrictions" != false ? getOk2Notify(pName) : getOk2Notify(pName) //parent?.getOk2Notify()

	LogAction("sendEventVoiceNotifications($vMsg, $pName) | ok2Notify: $ok2Notify", "info", false)
	if(allowNotif && allowSpeech){
		if(ok2Notify && (settings["${pName}SpeechDevices"] || settings["${pName}SpeechMediaPlayer"] || settings["${pName}EchoDevices"])){
			sendTTS(vMsg, pName)
		}
		if(settings["${pName}SendToAskAlexaQueue"]){		// we queue to Alexa regardless of quiet times
			if(rmMsgId != null && rmAAMsg == true){
				removeAskAlexaQueueMsg(rmMsgId)
			}
			if(vMsg && msgId != null){
				addEventToAskAlexaQueue(vMsg, msgId)
			}
		}
	}
}
*/

/*
def addEventToAskAlexaQueue(vMsg, msgId, queue=null){
	if(false){ //parent?.getAskAlexaMQEn() == true) {
		if(parent.getAskAlexaMultiQueueEn()){
			LogAction("sendEventToAskAlexaQueue: Adding this Message to the Ask Alexa Queue ($queues): ($vMsg)|${msgId}", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueue", value: "${app?.label}", isStateChange: true, descriptionText: "${vMsg}", unit: "${msgId}", data:queues)
		}else{
			LogAction("sendEventToAskAlexaQueue: Adding this Message to the Ask Alexa Queue: ($vMsg)|${msgId}", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueue", value: "${app?.label}", isStateChange: true, descriptionText: "${vMsg}", unit: "${msgId}")
		}
	}
}

def removeAskAlexaQueueMsg(msgId, queue=null){
	if(false){ //parent?.getAskAlexaMQEn() == true) {
		if(parent.getAskAlexaMultiQueueEn()){
			LogAction("removeAskAlexaQueueMsg: Removing Message ID (${msgId}) from the Ask Alexa Queue ($queues)", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueueDelete", value: "${app?.label}", isStateChange: true, unit: msgId, data: queues)
		}else{
			LogAction("removeAskAlexaQueueMsg: Removing Message ID (${msgId}) from the Ask Alexa Queue", "info", true)
			sendLocationEvent(name: "AskAlexaMsgQueueDelete", value: "${app?.label}", isStateChange: true, unit: msgId)
		}
	}
}
*/

void scheduleAlarmOn(String autoType){
	LogAction("scheduleAlarmOn: autoType: $autoType a1DelayVal: ${getAlert1DelayVal(autoType)}", "debug", false)
	Integer timeVal=getAlert1DelayVal(autoType)
	Boolean ok2Notify=true //setting?."${autoType}UseParentNotifRestrictions" != false ? getOk2Notify(autoType) : getOk2Notify(autoType) //parent?.getOk2Notify()

	LogAction("scheduleAlarmOn timeVal: $timeVal ok2Notify: $ok2Notify", "info", false)
	if(ok2Notify){
		if(timeVal > 0){
			runIn(timeVal, "alarm0FollowUp", [data: ["autoType": autoType]])
			LogAction("scheduleAlarmOn: Scheduling Alarm Followup 0 in timeVal: $timeVal", "info", false)
			state."${autoType}AlarmActive"=true
		}else{ LogAction("scheduleAlarmOn: Did not schedule ANY operation timeVal: $timeVal", "error", true) }
	}else{ LogAction("scheduleAlarmOn: Could not schedule operation timeVal: $timeVal", "error", true) }
}

void alarm0FollowUp(Map val){
	String autoType=val.autoType
	LogAction("alarm0FollowUp: autoType: $autoType 1 OffVal: ${getAlert1AlarmEvtOffVal(autoType)}", "debug", false)
	Integer timeVal=getAlert1AlarmEvtOffVal(autoType)
	LogAction("alarm0FollowUp timeVal: $timeVal", "info", false)
	if(timeVal > 0 && sendEventAlarmAction(1, autoType)){
		runIn(timeVal, "alarm1FollowUp", [data: ["autoType": autoType]])
		LogAction("alarm0FollowUp: Scheduling Alarm Followup 1 in timeVal: $timeVal", "info", false)
	}else{ LogAction ("alarm0FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

void alarm1FollowUp(Map val){
	String autoType=val.autoType
	LogAction("alarm1FollowUp autoType: $autoType a2DelayVal: ${getAlert2DelayVal(autoType)}", "debug", false)
	def aDev=settings["${autoType}AlarmDevices"]
	if(aDev){
		aDev?.off()
		storeLastAction("Set Alarm OFF", getDtNow(), "")
		LogAction("alarm1FollowUp: Turning OFF ${aDev}", "info", false)
	}
	Integer timeVal=getAlert2DelayVal(autoType)
	//if(canSchedule() && (settings["${autoType}_Alert_2_Use_Alarm"] && timeVal > 0)){
	if(timeVal > 0){
		runIn(timeVal, "alarm2FollowUp", [data: ["autoType": autoType]])
		LogAction("alarm1FollowUp: Scheduling Alarm Followup 2 in timeVal: $timeVal", "info", false)
	}else{ LogAction ("alarm1FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

void alarm2FollowUp(Map val){
	String autoType=val.autoType
	LogAction("alarm2FollowUp: autoType: $autoType 2 OffVal: ${getAlert2AlarmEvtOffVal(autoType)}", "debug", false)
	Integer timeVal=getAlert2AlarmEvtOffVal(autoType)
	if(timeVal > 0 && sendEventAlarmAction(2, autoType)){
		runIn(timeVal, "alarm3FollowUp", [data: ["autoType": autoType]])
		LogAction("alarm2FollowUp: Scheduling Alarm Followup 3 in timeVal: $timeVal", "info", false)
	}else{ LogAction ("alarm2FollowUp: Could not schedule operation timeVal: $timeVal", "error", true) }
}

void alarm3FollowUp(Map val){
	String autoType=val.autoType
	LogAction("alarm3FollowUp: autoType: $autoType", "debug", false)
	def aDev=settings["${autoType}AlarmDevices"]
	if(aDev){
		aDev?.off()
		storeLastAction("Set Alarm OFF", getDtNow(), "")
		LogAction("alarm3FollowUp: Turning OFF ${aDev}", "info", false)
	}
	state."${autoType}AlarmActive"=false
}

def alarmEvtSchedCleanup(String autoType){
	if(state."${autoType}AlarmActive"){
		LogAction("Cleaning Up Alarm Event Schedules autoType: $autoType", "info", false)
		List items=["alarm0FollowUp","alarm1FollowUp", "alarm2FollowUp", "alarm3FollowUp"]
		items.each {
			unschedule("$it")
		}
		def val=[ "autoType": autoType ]
		alarm3FollowUp(val)
	}
}

Boolean sendEventAlarmAction(Integer evtNum, String autoType){
	LogAction("sendEventAlarmAction evtNum: $evtNum autoType: $autoType", "info", false)
	try {
		Boolean resval=false
		Boolean allowNotif=settings."${autoType}NotifOn" ? true : false
		Boolean allowAlarm=allowNotif && settings."${autoType}AllowAlarmNotif"
		def aDev=settings["${autoType}AlarmDevices"]
		if(allowNotif && allowAlarm && aDev){
			//if(settings["${autoType}_Alert_${evtNum}_Use_Alarm"]){
				resval=true
				def alarmType=settings["${autoType}_Alert_${evtNum}_AlarmType"].toString()
				switch (alarmType){
					case "both":
						state."${autoType}alarmEvt${evtNum}StartDt"=getDtNow()
						aDev?.both()
						storeLastAction("Set Alarm BOTH ON", getDtNow(), autoType)
						break
					case "siren":
						state."${autoType}alarmEvt${evtNum}StartDt"=getDtNow()
						aDev?.siren()
						storeLastAction("Set Alarm SIREN ON", getDtNow(), autoType)
						break
					case "strobe":
						state."${autoType}alarmEvt${evtNum}StartDt"=getDtNow()
						aDev?.strobe()
						storeLastAction("Set Alarm STROBE ON", getDtNow(), autoType)
						break
					default:
						resval=false
						break
				}
			//}
		}
	} catch (ex){
		log.error "sendEventAlarmAction Exception: ($evtNum) -  ${ex?.message}"
		//parent?.sendExceptionData(ex, "sendEventAlarmAction", true, getAutoType())
	}
	return resval
}

void alarmAlertEvt(evt){
	LogAction("alarmAlertEvt: ${evt.displayName} Alarm State is Now (${evt.value})", "debug", false)
}

Integer getAlert1DelayVal(String autoType){ return !settings["${autoType}_Alert_1_Delay"] ? 300 : (settings["${autoType}_Alert_1_Delay"].toInteger()) }
Integer getAlert2DelayVal(String autoType){ return !settings["${autoType}_Alert_2_Delay"] ? 300 : (settings["${autoType}_Alert_2_Delay"].toInteger()) }

Integer getAlert1AlarmEvtOffVal(String autoType){ return !settings["${autoType}_Alert_1_Alarm_Runtime"] ? 10 : (settings["${autoType}_Alert_1_Alarm_Runtime"].toInteger()) }
Integer getAlert2AlarmEvtOffVal(String autoType){ return !settings["${autoType}_Alert_2_Alarm_Runtime"] ? 10 : (settings["${autoType}_Alert_2_Alarm_Runtime"].toInteger()) }

/*
Integer getAlarmEvt1RuntimeDtSec(){ return !state.alarmEvt1StartDt ? 100000 : GetTimeDiffSeconds(state.alarmEvt1StartDt).toInteger() }
Integer getAlarmEvt2RuntimeDtSec(){ return !state.alarmEvt2StartDt ? 100000 : GetTimeDiffSeconds(state.alarmEvt2StartDt).toInteger() }
*/

/*
void sendTTS(txt, pName){
	LogAction("sendTTS(data: ${txt})", "debug", false)
	try {
		def msg=txt?.toString()?.replaceAll("\\[|\\]|\\(|\\)|\\'|\\_", "")
		def spks=settings."${pName}SpeechDevices"
		def meds=settings."${pName}SpeechMediaPlayer"
		def echos=settings."${pName}EchoDevices"
		def res=settings."${pName}SpeechAllowResume"
		def vol=settings."${pName}SpeechVolumeLevel"
		LogAction("sendTTS msg: $msg | speaks: $spks | medias: $meds | echos: $echos| resume: $res | volume: $vol", "debug", false)
		if(settings."${pName}AllowSpeechNotif"){
			if(spks){
				spks*.speak(msg)
			}
			if(meds){
				meds?.each {
					if(res){
						def currentStatus=it.latestValue('status')
						def currentTrack=it.latestState("trackData")?.jsonValue
						def currentVolume=it.latestState("level")?.integerValue ? it.currentState("level")?.integerValue : 0
						if(vol){
							it?.playTextAndResume(msg, vol?.toInteger())
						}else{
							it?.playTextAndResume(msg)
						}
					}
					else {
						it?.playText(msg)
					}
				}
			}
			if(echos){
				echos*.setVolumeAndSpeak(settings."${pName}SpeechVolumeLevel", msg as String)
			}
		}
	} catch (ex){
		log.error "sendTTS Exception:", ex
		//parent?.sendExceptionData(ex, "sendTTS", true, getAutoType())
	}
}
*/

def scheduleTimeoutRestore(String pName){
	Integer timeOutVal=settings["${pName}OffTimeout"]?.toInteger()
	if(timeOutVal && !state."${pName}TimeoutScheduled"){
		runIn(timeOutVal.toInteger(), "restoreAfterTimeOut", [data: [pName:pName]])
		LogAction("Mode Restoration Timeout Scheduled ${pName} (${getEnumValue(longTimeSecEnum(), settings."${pName}OffTimeout")})", "info", true)
		state."${pName}TimeoutScheduled"=true
	}
}

def unschedTimeoutRestore(String pName){
	Integer timeOutVal=settings["${pName}OffTimeout"]?.toInteger()
	if(timeOutVal && state."${pName}TimeoutScheduled"){
		unschedule("restoreAfterTimeOut")
		LogAction("Cancelled Scheduled Mode Restoration Timeout ${pName}", "info", false)
	}
	state."${pName}TimeoutScheduled"=false
}

def restoreAfterTimeOut(val){
	String pName=val?.pName.value
	if(pName && settings."${pName}OffTimeout"){
		switch(pName){
			case "conWat":
				state."${pName}TimeoutScheduled"=false
				conWatCheck(true)
				break
			//case "leakWat":
				//leakWatCheck(true)
				//break
			case "extTmp":
				state."${pName}TimeoutScheduled"=false
				extTmpTempCheck(true)
				break
			default:
				LogAction("restoreAfterTimeOut no pName match ${pName}", "error", true)
				break
		}
	}
}

Boolean checkThermostatDupe(tstatOne, tstatTwo){
	Boolean result=false
	if(tstatOne && tstatTwo){
		String pTstat=tstatOne?.deviceNetworkId.toString()
		def mTstatAr=[]
		tstatTwo?.each { ts ->
			mTstatAr << ts?.deviceNetworkId.toString()
		}
		if(pTstat in mTstatAr){ return true }
	}
	return result
}

static Boolean checkModeDuplication(modeOne, modeTwo){
	Boolean result=false
	if(modeOne && modeTwo){
		modeOne?.each { dm ->
			if(dm in modeTwo){
				result=true
			}
		}
	}
	return result
}

private getDeviceSupportedCommands(dev){
	return dev?.supportedCommands.findAll { it as String }
}

Boolean checkFanSpeedSupport(dev){
	def req=["setSpeed"]
	Integer devCnt=0
	def devData=getDeviceSupportedCommands(dev)
	devData.each { cmd ->
		if(cmd.name in req){ devCnt=devCnt+1 }
	}
	def t0=dev?.currentSpeed
	def speed=t0 ?: null
	//log.debug "checkFanSpeedSupport (speed: $speed | devCnt: $devCnt)"
	return speed && devCnt==1
}

void getTstatCapabilities(tstat, String autoType, Boolean dyn=false){
	try {
		Boolean canCool=true
		Boolean canHeat=true
		Boolean hasFan=true
		if(tstat?.currentCanCool){ canCool=tstat?.currentCanCool.toBoolean() }
		if(tstat?.currentCanHeat){ canHeat=tstat?.currentCanHeat.toBoolean() }
		if(tstat?.currentHasFan){ hasFan=tstat?.currentHasFan.toBoolean() }

		state."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatCanCool"=canCool
		state."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatCanHeat"=canHeat
		state."${autoType}${dyn ? "_${tstat?.deviceNetworkId}_" : ""}TstatHasFan"=hasFan
	} catch (ex){
		log.error "getTstatCapabilities Exception: ${ex?.message}"
	}
}

def getSafetyTemps(tstat, usedefault=true){
	def minTemp=tstat?.currentSafetyTempMin?.doubleValue
	def maxTemp=tstat?.currentSafetyTempMax?.doubleValue
	if(minTemp == 0){
		if(usedefault){ minTemp=(getTemperatureScale() == "C") ? 7 : 45 }
		else { minTemp=null }
	}
	if(maxTemp == 0){ maxTemp=null }
	if(minTemp || maxTemp){
		return ["min":minTemp, "max":maxTemp]
	}
	return null
}

def getComfortDewpoint(tstat, usedefault=true){
	def maxDew=tstat?.currentComfortDewpointMax?.doubleValue
	maxDew=maxDew ?: 0.0
	if(maxDew == 0.0){
		if(usedefault){
			maxDew=(getTemperatureScale() == "C") ? 19 : 66
			return maxDew.toDouble()
		}
		return null
	}
	return maxDew
}

Boolean getSafetyTempsOk(tstat){
	def sTemps=getSafetyTemps(tstat)
	//log.debug "sTempsOk: $sTemps"
	if(sTemps){
		def curTemp=tstat?.currentTemperature?.toDouble()
		//log.debug "curTemp: ${curTemp}"
		if( ((sTemps?.min != null && sTemps?.min.toDouble() != 0) && (curTemp < sTemps?.min.toDouble())) || ((sTemps?.max != null && sTemps?.max?.toDouble() != 0) && (curTemp > sTemps?.max?.toDouble())) ){
			return false
		}
	} //else{ log.debug "getSafetyTempsOk: no safety Temps" }
	return true
}

def getGlobalDesiredHeatTemp(){
	Double t0=null //parent?.settings.locDesiredHeatTemp?.toDouble()
	return t0 ?: null
}

def getGlobalDesiredCoolTemp(){
	Double t0=null // parent?.settings.locDesiredCoolTemp?.toDouble()
	return t0 ?: null
}
/*
def getClosedContacts(contacts){
	if(contacts){
		def cnts=contacts?.findAll { it?.currentContact == "closed" }
		return cnts ?: null
	}
	return null
}
*/
def getOpenContacts(contacts){
	if(contacts){
		def cnts=contacts?.findAll { it?.currentContact == "open" }
		return cnts ?: null
	}
	return null
}
/*
def getDryWaterSensors(sensors){
	if(sensors){
		def cnts=sensors?.findAll { it?.currentWater == "dry" }
		return cnts ?: null
	}
	return null
}
*/
def getWetWaterSensors(sensors){
	if(sensors){
		def cnts=sensors?.findAll { it?.currentWater == "wet" }
		return cnts ?: null
	}
	return null
}
/*
Boolean isContactOpen(con){
	Boolean res=false
	if(con){
		if(con?.currentSwitch == "on"){ res=true }
	}
	return res
}
*/
Boolean isSwitchOn(dev){
	Boolean res=false
	if(dev){
		dev?.each { d ->
			if(d?.currentSwitch == "on"){ res=true }
		}
	}
	return res
}

Boolean isPresenceHome(presSensor){
	Boolean res=false
	if(presSensor){
		presSensor?.each { d ->
			if(d?.currentPresence == "present"){ res=true }
		}
	}
	return res
}

Boolean isSomebodyHome(sensors){
	if(sensors){
		def cnts=sensors?.findAll { it?.currentPresence == "present" }
		return cnts ? true : false
	}
	return false
}

String getTstatPresence(tstat){
	String pres="not present"
	if(tstat){ pres=tstat?.currentPresence }
	return pres
}

Boolean setTstatMode(tstat, String mode, String autoType=null){
	Boolean result=false
	if(mode && tstat){
		String curMode=tstat?.currentThermostatMode?.toString()
		if(curMode != mode){
			try {
				if(mode == "auto"){ tstat.auto(); result=true }
				else if(mode == "heat"){ tstat.heat(); result=true }
				else if(mode == "cool"){ tstat.cool(); result=true }
				else if(mode == "off"){ tstat.off(); result=true }
				else {
					if(mode == "eco"){
						tstat.eco(); result=true
						LogTrace("setTstatMode mode action | type: $autoType")
//						if(autoType){ sendEcoActionDescToDevice(tstat, autoType) } // THIS ONLY WORKS ON NEST THERMOSTATS
					}
				}
			}
			catch (ex){
				log.error "setTstatMode() Exception: ${tstat?.label} does not support mode ${mode}; check IDE and install instructions ${ex?.message}"
				//parent?.sendExceptionData(ex, "setTstatMode", true, getAutoType())
			}
		}

		if(result){ LogAction("setTstatMode: '${tstat?.label}' Mode set to (${strCapitalize(mode)})", "info", false) }
		else { LogAction("setTstatMode() | No Mode change: ${mode}", "info", false) }
	}else{
		LogAction("setTstatMode() | Invalid or Missing Mode received: ${mode}", "warn", true)
	}
	return result
}

Boolean setMultipleTstatMode(tstats, String mode, String autoType=null){
	Boolean result=false
	if(tstats && mode){
		tstats?.each { ts ->
			Boolean retval
//			try {
				retval=setTstatMode(ts, mode, autoType)
//			} catch (ex){
//				log.error "setMultipleTstatMode() Exception:", ex
//				parent.sendExceptionData(ex, "setMultipleTstatMode", true, getAutoType())
//			}

			if(retval){
				LogAction("Setting ${ts?.displayName} Mode to (${mode})", "info", false)
				storeLastAction("Set ${ts?.displayName} to (${mode})", getDtNow(), autoType)
				result=true
			}else{
				LogAction("Failed Setting ${ts} Mode to (${mode})", "warn", true)
				return false
			}
		}
	}else{
		LogAction("setMultipleTstatMode(${tstats}, $mode, $autoType) | Invalid or Missing tstats or Mode received: ${mode}", "warn", true)
	}
	return result
}

Boolean setTstatAutoTemps(tstat, coolSetpoint, heatSetpoint, String pName, mir=null){
	Boolean retVal=false
	String setStr="No thermostat device"
	Boolean heatFirst
	def setHeat
	def setCool
	String hvacMode="unknown"
	def reqCool
	def reqHeat
	def curCoolSetpoint
	def curHeatSetpoint
	String tempScaleStr=tUnitStr()

	if(tstat){
		hvacMode=tstat.currentThermostatMode.toString()
		LogTrace(tStr)

		retVal=true
		setStr="Error: "

		curCoolSetpoint=getTstatSetpoint(tstat, "cool")
		curHeatSetpoint=getTstatSetpoint(tstat, "heat")
		def diff=getTemperatureScale() == "C" ? 2.0 : 3.0
		reqCool=coolSetpoint?.toDouble() ?: null
		reqHeat=heatSetpoint?.toDouble() ?: null

		if(!reqCool && !reqHeat){ retVal=false; setStr += "Missing COOL and HEAT Setpoints" }

		if(hvacMode in ["auto"]){
			if(!reqCool && reqHeat){ reqCool=(Double) ((curCoolSetpoint > (reqHeat + diff)) ? curCoolSetpoint : (reqHeat + diff)) }
			if(!reqHeat && reqCool){ reqHeat=(Double) ((curHeatSetpoint < (reqCool - diff)) ? curHeatSetpoint : (reqCool - diff)) }
			if((reqCool && reqHeat) && (reqCool >= (reqHeat + diff))){
				if(reqHeat <= curHeatSetpoint){ heatFirst=true }
					else if(reqCool >= curCoolSetpoint){ heatFirst=false }
					else if(reqHeat > curHeatSetpoint){ heatFirst=false }
					else { heatFirst=true }
				if(heatFirst){
					if(reqHeat != curHeatSetpoint){ setHeat=true }
					if(reqCool != curCoolSetpoint){ setCool=true }
				}else{
					if(reqCool != curCoolSetpoint){ setCool=true }
					if(reqHeat != curHeatSetpoint){ setHeat=true }
				}
			}else{
				setStr += " or COOL/HEAT is not separated by ${diff}"
				retVal=false
			}

		}else if(hvacMode in ["cool"] && reqCool){
			if(reqCool != curCoolSetpoint){ setCool=true }

		}else if(hvacMode in ["heat"] && reqHeat){
			if(reqHeat != curHeatSetpoint){ setHeat=true }

		}else{
			setStr += "incorrect HVAC Mode (${hvacMode})"
			retVal=false
		}
	}
	if(retVal){
		setStr="Setting: "
		if(heatFirst && setHeat){
			setStr += "heatSetpoint: (${reqHeat}${tempScaleStr}) "
			if(reqHeat != curHeatSetpoint){
				tstat?.setHeatingSetpoint(reqHeat)
				storeLastAction("Set ${tstat} Heat Setpoint ${reqHeat}${tempScaleStr}", getDtNow(), pName, tstat)
				if(mir){ mir*.setHeatingSetpoint(reqHeat) }
			}
		}
		if(setCool){
			setStr += "coolSetpoint: (${reqCool}${tempScaleStr}) "
			if(reqCool != curCoolSetpoint){
				tstat?.setCoolingSetpoint(reqCool)
				storeLastAction("Set ${tstat} Cool Setpoint ${reqCool}", getDtNow(), pName, tstat)
				if(mir){ mir*.setCoolingSetpoint(reqCool) }
			}
		}
		if(!heatFirst && setHeat){
			setStr += "heatSetpoint: (${reqHeat}${tempScaleStr})"
			if(reqHeat != curHeatSetpoint){
				tstat?.setHeatingSetpoint(reqHeat)
				storeLastAction("Set ${tstat} Heat Setpoint ${reqHeat}${tempScaleStr}", getDtNow(), pName, tstat)
				if(mir){ mir*.setHeatingSetpoint(reqHeat) }
			}
		}
	}
	String tStr="setTstatAutoTemps: [tstat: ${tstat?.displayName} | Mode: ${hvacMode} | coolSetpoint: ${coolSetpoint}${tempScaleStr} | heatSetpoint: ${heatSetpoint}${tempScaleStr}] "
	LogAction(tStr+setStr, retVal ? "info" : "warn", true)
	return retVal
}


/******************************************************************************
*					Keep These Methods						*
*******************************************************************************/
/*
def switchEnumVals(){ return [0:"Off", 1:"On", 2:"On/Off"] }

def longTimeMinEnum(){
	def vals=[
		1:"1 Minute", 2:"2 Minutes", 3:"3 Minutes", 4:"4 Minutes", 5:"5 Minutes", 10:"10 Minutes", 15:"15 Minutes", 20:"20 Minutes", 25:"25 Minutes", 30:"30 Minutes",
		45:"45 Minutes", 60:"1 Hour", 120:"2 Hours", 240:"4 Hours", 360:"6 Hours", 720:"12 Hours", 1440:"24 Hours"
	]
	return vals
}
*/

Map fanTimeSecEnum(){
	Map vals=[
		60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes"
	]
	return vals
}

Map longTimeSecEnum(){
	Map vals=[
		0:"Off", 60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 2700:"45 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 10:"10 Seconds(Testing)"
	]
	return vals
}

Map shortTimeEnum(){
	Map vals=[
		1:"1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds",
		8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds", 15:"15 Seconds", 30:"30 Seconds", 60:"60 Seconds"
	]
	return vals
}

Map switchRunEnum(Boolean addAlways=false){
	String pName=schMotPrefix()
	Boolean hasFan=state."${pName}TstatHasFan"
	Boolean canCool=state."${pName}TstatCanCool"
	Boolean canHeat=state."${pName}TstatCanHeat"
	Map vals=[ 1:"Any operation: Heating or Cooling" ]
	if(hasFan){
		vals << [2:"With HVAC Fan Only"]
	}
	if(canHeat){
		vals << [3:"Heating"]
	}
	if(canCool){
		vals << [4:"Cooling"]
	}
	if(addAlways){
		vals << [5:"Any Operating or non-operating State"]
	}
	return vals
}

Map fanModeTrigEnum(){
	String pName=schMotPrefix()
	Boolean canCool=state."${pName}TstatCanCool"
	Boolean canHeat=state."${pName}TstatCanHeat"
	Boolean hasFan=state."${pName}TstatHasFan"
	Map vals=["auto":"Auto", "cool":"Cool", "heat":"Heat", "eco":"Eco", "any":"Any Mode"]
	if(!canHeat){
		vals=["cool":"Cool", "eco":"Eco", "any":"Any Mode"]
	}
	if(!canCool){
		vals=["heat":"Heat", "eco":"Eco", "any":"Any Mode"]
	}
	return vals
}

Map tModeHvacEnum(Boolean canHeat, Boolean canCool, Boolean canRtn=false){
	Map vals=["auto":"Auto", "cool":"Cool", "heat":"Heat", "eco":"Eco"]
	if(!canHeat){
		vals=["cool":"Cool", "eco":"Eco"]
	}
	if(!canCool){
		vals=["heat":"Heat", "eco":"Eco"]
	}
	if(canRtn){
		vals << ["rtnFromEco":"Return from ECO if in ECO"]
	}
	return vals
}

Map alarmActionsEnum(){
	Map vals=["siren":"Siren", "strobe":"Strobe", "both":"Both (Siren/Strobe)"]
	return vals
}

def getEnumValue(enumName, inputName){
	def result="unknown"
	List resultList=[]
	Boolean inputIsList=getObjType(inputName) == "List" ? true : false
	if(enumName){
		enumName?.each { item ->
			if(inputIsList){
				inputName?.each { inp ->
					if(item.key.toString() == inp?.toString()){
						resultList.push(item.value)
					}
				}
			}else
			if(item.key.toString() == inputName?.toString()){
				result=item.value
			}
		}
	}
	if(inputIsList){
		return resultList
	}else{
		return result
	}
}

/*
def getSunTimeState(){
	def tz=TimeZone.getTimeZone(location.timeZone.ID)
	def sunsetTm=Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", location?.currentValue('sunsetTime')).format('h:mm a', tz)
	def sunriseTm=Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSX", location?.currentValue('sunriseTime')).format('h:mm a', tz)
	state.sunsetTm=sunsetTm
	state.sunriseTm=sunriseTm
}

def parseDt(String format, dt){
	def result
	Date newDt=Date.parse(format, dt)
	result=formatDt(newDt)
	//log.debug "result: $result"
	return result
}
*/


/******************************************************************************
*								STATIC METHODS								*
*******************************************************************************/

//def getAutoAppChildName()	{ return "Nest Automations" }
String getWatDogAppChildName()	{ return "Nest Location ${location.name} Watchdog" }

//def getChildName(str)		{ return "${str}" }

String getChildAppVer(appName){ return appName?.appVersion() ? "v${appName?.appVersion()}" : "" }
//Boolean getUse24Time()			{ return settings.useMilitaryTime }

//Returns app State Info
Integer getStateSize(){
	def resultJson=new groovy.json.JsonOutput().toJson(state)
	return resultJson?.toString().length()
}
Integer getStateSizePerc()		{ return (Integer) ((stateSize / 100000)*100).toDouble().round(0) }

List getLocationModes(){
	List result=[]
	location.modes.sort().each {
		if(it){ result.push("${it}") }
	}
	return result
}

static String getObjType(obj){
	if(obj instanceof String){return "String"}
	else if(obj instanceof Map){return "Map"}
	else if(obj instanceof List){return "List"}
	else if(obj instanceof ArrayList){return "ArrayList"}
	else if(obj instanceof Integer){return "Integer"}
	else if(obj instanceof BigInteger){return "BigInteger"}
	else if(obj instanceof Long){return "Long"}
	else if(obj instanceof Boolean){return "Boolean"}
	else if(obj instanceof BigDecimal){return "BigDecimal"}
	else if(obj instanceof Float){return "Float"}
	else if(obj instanceof Byte){return "Byte"}
	else { return "unknown"}
}

static Map preStrObj(){ [1:"•", 2:"│", 3:"├", 4:"└", 5:"    "] }

//def getShowHelp(){ return state.showHelp == false ? false : true }

def getTimeZone(){
	def tz=null
	if(location?.timeZone){ tz=location?.timeZone }
	//else { tz=getNestTimeZone() ? TimeZone.getTimeZone(getNestTimeZone()) : null }
	if(!tz){ LogAction("getTimeZone: Hub or Nest TimeZone not found", "warn", true) }
	return tz
}

String formatDt(Date dt){
	def tf=new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()){ tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("HE TimeZone is not set; Please open your location and Press Save", "warn", true)
	}
	return tf.format(dt)
}

String getGlobTitleStr(typ){
	return "Desired Default ${typ} Temp (${tUnitStr()})"
}

String formatDt2(tm){
	//def formatVal=settings.useMilitaryTime ? "MMM d, yyyy - HH:mm:ss" : "MMM d, yyyy - h:mm:ss a"
	String formatVal="MMM d, yyyy - h:mm:ss a"
	def tf=new SimpleDateFormat(formatVal)
	if(getTimeZone()){ tf.setTimeZone(getTimeZone()) }
	return tf.format(Date.parse("E MMM dd HH:mm:ss z yyyy", tm.toString()))
}

String tUnitStr(){
	return "\u00b0"+getTemperatureScale()
}

/*
void updTimestampMap(keyName, dt=null){
	def data=state.timestampDtMap ?: [:]
	if(keyName){ data[keyName]=dt }
	state.timestampDtMap=data
}

def getTimestampVal(val){
	def tsData=state.timestampDtMap
	if(val && tsData && tsData[val]){ return tsData[val] }
	return null
}
*/

private Integer getTimeSeconds(String timeKey, Integer defVal, String meth){
	String t0=state."${timeKey}" //=getTimestampVal(timeKey)
	return !t0 ? defVal : GetTimeDiffSeconds(t0, (String)null, meth).toInteger()
}

Long GetTimeDiffSeconds(String strtDate, String stpDate=(String)null, String methName=(String)null){
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)){
		//if(strtDate?.contains("dtNow")){ return 10000 }
		Date now=new Date()
		String stopVal=stpDate ? stpDate.toString() : formatDt(now)
		Long start=Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stop=Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Long diff=(stop - start) / 1000L
		LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	}else{ return null }
}
/*
Boolean daysOk(days){
	if(days){
		def dayFmt=new SimpleDateFormat("EEEE")
		if(getTimeZone()){ dayFmt.setTimeZone(getTimeZone()) }
		return days.contains(dayFmt.format(new Date())) ? false : true
	}else{ return true }
}
*/
String time2Str(String time){
	if(time){
		Date t=timeToday(time, getTimeZone())
		def f=new java.text.SimpleDateFormat("h:mm a")
		f.setTimeZone(getTimeZone() ?: timeZone(time))
		f.format(t)
	}
}
/*
String epochToTime(Long tm){
	def tf=new SimpleDateFormat("h:mm a")
		tf?.setTimeZone(getTimeZone())
	return tf.format(tm)
}
*/
String getDtNow(){
	Date now=new Date()
	return formatDt(now)
}

Boolean modesOk(List modeEntry){
	Boolean res=true
	if(modeEntry){
		modeEntry?.each { m ->
			if(m.toString() == location.mode.toString()){ res=false }
		}
	}
	return res
}

Boolean isInMode(List modeList){
	if(modeList){
		//log.debug "mode (${location.mode}) in list: ${modeList} | result: (${location.mode in modeList})"
		return location.mode.toString() in modeList
	}
	return false
}

static Map notifValEnum(Boolean allowCust=true){
	Map valsC=[
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes", 1800:"30 Minutes",
		3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 1000000:"Custom"
	]
	Map vals=[
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
	]
	return allowCust ? valsC : vals
}

static Map pollValEnum(){
	Map vals=[
		60:"1 Minute", 120:"2 Minutes", 180:"3 Minutes", 240:"4 Minutes", 300:"5 Minutes",
		600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 2700:"45 Minutes", 3600:"60 Minutes"
	]
	return vals
}

static Map waitValEnum(){
	Map vals=[
		1:"1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds", 6:"6 Seconds", 7:"7 Seconds",
		8:"8 Seconds", 9:"9 Seconds", 10:"10 Seconds", 15:"15 Seconds", 30:"30 Seconds"
	]
	return vals
}

static String strCapitalize(str){
	return str ? str.toString().capitalize() : null
}

static String getInputEnumLabel(inputName, enumName){
	String result="Not Set"
	if(inputName && enumName){
		enumName.each { item ->
			if(item?.key.toString() == inputName?.toString()){
				result=item?.value
			}
		}
	}
	return result
}

String toJson(Map m){
	return new org.json.JSONObject(m).toString()
}

def toQueryString(Map m){
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

/************************************************************************************************
|									LOGGING AND Diagnostic										|
*************************************************************************************************/

static String lastN(String input, n){
	return n > input?.size() ? input : input[-n..-1]
	//return n > input?.size() ? input : n ? input[-n..-1] : ''
}

void LogTrace(GString msg, String logSrc=(String)null){
	LogTrace(msg.toString(), logSrc)
}

void LogTrace(String msg, String logSrc=(String)null){
	Boolean trOn=(settings.showDebug && settings.advAppDebug) ? true : false
	if(trOn){
		Boolean logOn=(settings.enRemDiagLogging && state.enRemDiagLogging) ? true : false
		Logger(msg, "trace", logSrc, logOn)
	}
}

void LogAction(GString msg, String type="debug", Boolean showAlways=false, String logSrc=null){
	LogAction(msg.toString(), type, showAlways, logSrc)
}

void LogAction(String msg, String type="debug", Boolean showAlways=false, String logSrc=null){
	def isDbg=settings.showDebug ? true : false
	if(showAlways || (isDbg && !showAlways)){ Logger(msg, type, logSrc) }
}

void Logger(String msg, String type="debug", String logSrc=(String)null, Boolean noSTlogger=false){
	if(msg && type){
		String labelstr=""
		if(state.dbgAppndName == null){
			Boolean tval=parent ? parent.getSettingVal("dbgAppndName") : settings.dbgAppndName
			state.dbgAppndName=(tval || tval == null) ? true : false
		}
		String t0=app.label
		if((Boolean)state.dbgAppndName){ labelstr=t0+' | ' }
		String themsg=labelstr+msg
		//log.debug "Logger remDiagTest: $msg | $type | $logSrc"

		if(state.enRemDiagLogging == null){
			state.enRemDiagLogging=parent.getStateVal("enRemDiagLogging")
			if(state.enRemDiagLogging == null){
			       state.enRemDiagLogging=false
			}
			//log.debug "set enRemDiagLogging to ${state.enRemDiagLogging}"
		}
		if((Boolean)state.enRemDiagLogging){
			String theId=lastN(app.id.toString(),5)
			String theLogSrc=(logSrc == (String)null) ? (parent ? 'Automation-'+theId : "NestManager") : logSrc
			parent.saveLogtoRemDiagStore(themsg, type, theLogSrc)
		}else{
			if(!noSTlogger){
				switch(type){
				case "debug":
					log.debug themsg
					break
				case "info":
					log.info '| '+themsg
					break
				case "trace":
					log.trace '| '+themsg
					break
				case "error":
					log.error '| '+themsg
					break
				case "warn":
					log.warn '|| '+themsg
					break
				default:
					log.debug themsg
					break
				}
			}
		}
	}else{ log.error "${labelstr}Logger Error - type: ${type} | msg: ${msg} | logSrc: ${logSrc}" }
}

///////////////////////////////////////////////////////////////////////////////
/******************************************************************************
|				Application Help and License Info Variables					|
*******************************************************************************/
///////////////////////////////////////////////////////////////////////////////
static String appName()		{ return appLabel() }
static String appLabel()	{ return "NST Automations" }
static String gitRepo()		{ return "tonesto7/nest-manager"}
static String gitBranch()	{ return "master" }
static String gitPath()		{ return gitRepo()+'/'+gitBranch() }
