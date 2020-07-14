/**
 *  NST Manager
 *	Copyright (C) 2017-2020 Anthony Santilli
 *	Author: Anthony Santilli (@tonesto7) Eric Schott (@nh.schottfam)
 * June 24, 2020
 */

import groovy.json.*
import java.text.SimpleDateFormat
import java.security.MessageDigest
import groovy.transform.Field

definition(
	name: "NST Manager",
	namespace: "tonesto7",
	author: "Anthony S.",
	description: "Integrate your Nest products into your Hubitat Elevation Enviroment",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/tonesto7/nst-manager-he/master/apps/nstManager.groovy",
	singleInstance: true,
	oauth: true
)

static String appVer() { "2.0.8" }
static String namespace()  { "tonesto7" }
static Integer devCltNum() { 1 }
static Boolean restEnabled(){ true } // Enables the Rest Stream Device
static Integer DevPoll() { 60 } // 1 minute poll time (when rest is not active)
static Integer StrPoll() { 120 } // 2 minute poll time (when rest is not active)
static Integer MetaPoll() { 14400 } // 4 hrs poll time (when rest is not active)
static Integer refreshWait() { 10 } // Restricts Manual Refreshes to every every 10 seconds
static Integer tempChgWaitVal() { 3 } // This is the wait time after manually changing temp before sending the command.  It allows successive changes and avoids exceeding nest command limits

preferences {
	page(name: "authPage")
	page(name: "mainPage")
	page(name: "deviceSelectPage")
	page(name: "reviewSetupPage")
	page(name: "debugPrefPage")
	page(name: "devNamePage")
//	page(name: "devNameResetPage")
	page(name: "nestLoginPrefPage")
	page(name: "nestTokenResetPage")
	page(name: "automationsPage")
	page(name: "automationSchedulePage")
	page(name: "notifPrefPage")
}

mappings {
	//used during Oauth Authentication
	path("/initialize")	{action: [GET: "oauthInitUrl"]}
	path("/callback")	{action: [GET: "callback"]}
}

@Field static final String sNULL=(String)null

/******************************************************************************
 |					Application Pages			|
 *******************************************************************************/
def appInfoSect()	{
	section() {
		String str = """
		<div class="appInfoSect" style="width: 300px; height: 70px; display: inline-table;">
			<ul style=" margin: 0 auto; padding: 0; list-style-type: none;">
			<img style="float: left; padding: 10px;" src="${getAppImg("nest_icon128.png")}" width="60px"/>
			<li style="padding-top: 2px;"><b><i>${app.name}</i></b></li>
			<li><small style="color: black !important;">Version: ${appVer()}</small></li>
			</ul>
		</div>
		<script>\$('.appInfoSect').parent().css("cssText", "font-family: Arial !important; white-space: inherit !important;")</script>
		""".toString()
		paragraph str
	}
}

def authPage() {
	//LogTrace("authPage()")
	String description

	if(getNestAuthToken()!=sNULL) {
		description = "<i>You are connected.</i>"
 		return mainPage()
	} else {
		description = "<i>Tap to enter Nest Login Credentials</i>"
		if(!(String)state.access_token) {
			getAccessToken()
			if(!(String)state.access_token) { enableOauth(); getAccessToken() }
		}
		Boolean ok4Main = ((String)state.access_token && nestDevAccountCheckOk())
		return dynamicPage(name: "authPage", title: "", nextPage: ok4Main ? "mainPage" : "", install: false, uninstall: false) {
			if(!ok4Main) {
				section () {
					String title
					String desc
					if(!(String)state.access_token) {
						title = "OAuth Error"
						desc = "OAuth is not Enabled for ${app.name} application.  Please click remove and review the installation directions again".toString()
					} else if(!nestDevAccountCheckOk()) {
						title = "Nest Developer Data Missing"
						desc = "Client ID and Secret\nAre both missing!\n\nThe built-in Client ID and Secret can no longer be provided.\n\nPlease visit the Wiki at the link below to resolve the issue."
					} else {
						desc = "Application Status has not received any messages to display"
					}
					LogAction('Status Message: '+desc, "warn", true)
					paragraph desc, required: true, state: sNULL
				}
			}
			section () {
				input(name: "useMyClientId", type: "bool", title: imgTitle(getAppImg("i_lg"), inputTitleStr("Enter your own ClientId?")), required: false, defaultValue: false, submitOnChange: true)
				if(useMyClientId) {
					input("clientId", "text", title: imgTitle(getAppImg("i_lg"), inputTitleStr("Nest ClientId")), defaultValue: "", required: true, submitOnChange: true, image: getAppImg("i_lg"))
					input("clientSecret", "text", title: imgTitle(getAppImg("i_lg"), inputTitleStr("Nest Client Secret")), defaultValue: "", required: true, submitOnChange: true, image: getAppImg("i_lg"))
				} else {
//					settingUpdate("clientId", "")
//					settingUpdate("clientSecret", "")
				}
			}
			if(ok4Main && getNestAuthToken()==sNULL) {
				String redirectUrl = getOauthInitUrl()
				LogTrace("AuthToken not found: Directing to Login Page")
				section(sectionTitleStr("Nest Authorization Page")) {
					String txt = '<ul style="padding-left: 15px; text-align: left;">'
					txt += "<li>Tap <b><i><u>Login to Nest</u></i></b> below to authorize Hubitat to access your Nest Account.</li>"
					txt += "<li>You will be taken to the <u>Works with Nest</u> login page.</li>"
					txt += "<li>Read the permission descriptions and if you Agree press the <b>Accept</b> button.</li>"
					txt += "<li>You will be redirected back to this page to select your Nest location.</li>"
					txt += "</ul>"
					paragraph txt
					href url: redirectUrl, style:"external", required: true, title: inputTitleStr("Login to Nest"), description: description
					paragraph '<p style="color: red;">NOTICE: Please use the parent Nest account, Nest Family member accounts will not work correctly</p>', state: "complete"
				}
			}
		}
	}
}

def mainPage() {
	//LogTrace("mainPage")
	Boolean isInstalled = (Boolean)state.isInstalled
	Boolean setupComplete = (isInstalled == true)
	return dynamicPage(name: "mainPage", title: "", nextPage: (!setupComplete ? "reviewSetupPage" : sNULL), install: true, uninstall: isInstalled) {
		appInfoSect()
		String ttm_str = descriptions('d_ttm')
		String ttc_str = descriptions('d_ttc')
		if(isInstalled) {
			if((String)settings.structures && !(String)state.structures) { state.structures = (String)settings.structures }
			section(sectionTitleStr("Nest Location Mode:")) {
				String pres = getLocationPresence()
				String color = (pres == "away") ? "orange" : (pres == "home" ? "#00c9ff" : sNULL)
				paragraph imgTitle(getAppImg("home_icon.png"), strCapitalize(pres ?: "Not Available Yet!"), color), state: "complete"
			}
			section(sectionTitleStr("Devices & Location:")) {
				String t1 = getDevicesDesc(false)
				String devDesc = t1 ? t1+'\n\n'+ttm_str : ttc_str
				href "deviceSelectPage", title: inputTitleStr("Manage/View Devices"), description: devDesc, state: "complete"
			}
		}
		if(!isInstalled) { devicesPage() }
		if(isInstalled) {
			if((String)state.structures && ((Map)state.thermostats || (Map)state.protects || (Map)state.cameras)) {
				String t1 = getInstAutoTypesDesc()
				String autoDesc = t1 ? t1+'\n\n'+ttm_str : ttc_str
				section("Manage Automations:") {
					href "automationsPage", title: imgTitle(getAppImg("nst_automations_5.png"), inputTitleStr("Automations")), description: autoDesc, state: (t1 ? "complete" : sNULL)
				}

			}
			section("Notifications Options:") {
				String t1 = getAppNotifConfDesc()
				href "notifPrefPage", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Notifications")), description: (t1 ? t1+'\n\n'+ttm_str : ttc_str), state: (t1 ? "complete" : sNULL)
			}
			section(sectionTitleStr("Nest Authentication:")) {
				String t1 = getNestAuthToken()!=sNULL ? "Nest Authorized\n<b>Last API Connection:</b>\n• ${getTimestampVal("lastDevDataUpd")}" : ""
				String authDesc = t1 ? t1+'\n\n'+ttm_str : ttc_str
				href "nestLoginPrefPage", title: imgTitle(getAppImg("i_lg"), inputTitleStr("Manage Login")), description: authDesc, state: (t1 ? "complete" : sNULL)
			}
			section(sectionTitleStr("App Logging:")) {
				String t1 = getAppDebugDesc()
				href "debugPrefPage", title: imgTitle(getAppImg("log.png"), inputTitleStr("Configure Logging")), description: (t1 ? t1+'\n\n'+ttm_str : ttc_str), state: t1 ? "complete" : sNULL
			}
		}
	}
}

def deviceSelectPage() {
	Boolean isInstalled = (Boolean)state.isInstalled
	return dynamicPage(name: "deviceSelectPage", title: pageTitleStr("Device Selection"), nextPage: (!isInstalled ? "mainPage" : sNULL), install: true, uninstall: false) {
		devicesPage()
	}
}

def devicesPage() {
	Map structs = getNestStructures()
	Boolean isInstalled = (Boolean)state.isInstalled
	Integer strucSz = (Integer)structs.size()
	String structDesc = strucSz==0 ? "No Locations Found" : strucSz.toString()+' Found'
	if((Map)state.thermostats || (Map)state.protects || (Map)state.cameras || (Boolean)state.presDevice ) {  // if devices are configured, you cannot change the structure until they are removed
		section(sectionTitleStr("Nest Location: "+'( '+structDesc+' )')) {
			paragraph imgTitle(getAppImg("i_ns"), inputTitleStr("Name:")+' '+(String)structs[(String)state.structures]+"${(structs.size() > 1) ? "\n(Remove All Devices to Change!)" : ""}")
		}
	} else {
		section(sectionTitleStr("Select Location:")) {
			input(name: "structures", title: imgTitle(getAppImg("i_ns"), inputTitleStr("Available Locations")), type: "enum", required: true, multiple: false, submitOnChange: true, options: structs)
		}
	}
	if((String)settings.structures) {
		state.structures = (String)settings.structures
		String newStrucName = structs && structs."${(String)state.structures}" ? (String)structs[(String)state.structures] : sNULL
		state.structureName = newStrucName ?: (String)state.structureName

		Map stats = getNestThermostats()
		Map coSmokes = getNestProtects()
		Map cams = getNestCameras()

		section(sectionTitleStr("Select Devices:")) {
			if(!stats?.size() && !coSmokes.size() && !cams?.size()) { paragraph "<h2>No Devices were found</h2>" }
			if(stats?.size() > 0) {
				input(name: "thermostats", title: imgTitle(getAppImg("i_th"), """<u>Nest Thermostats</u><small style="color: blue !important;"> (${stats?.size()} found)</small>""".toString()), type: "enum", required: false, multiple: true, submitOnChange: true, options:stats)
			}
			state.thermostats = (List)settings.thermostats ? statState((List)settings.thermostats) : null
			if(coSmokes.size() > 0) {
				input(name: "protects", title: imgTitle(getAppImg("i_p"), """<u>Nest Protects</u><small style="color: blue !important;"> (${coSmokes?.size()} found)</small>""".toString()), type: "enum", required: false, multiple: true, submitOnChange: true, options: coSmokes)
			}
			state.protects = (List)settings.protects ? coState((List)settings.protects) : null
			if(cams.size() > 0) {
				input(name: "cameras", title: imgTitle(getAppImg("i_c"), """<u>Nest Cameras</u><small style="color: blue !important;"> (${cams?.size()} found)</small>""".toString()), type: "enum", required: false, multiple: true, submitOnChange: true, options: cams)
			}
			state.cameras = (List)settings.cameras ? camState((List)settings.cameras) : null
			input(name: "presDevice", title: imgTitle(getAppImg("i_pr"), inputTitleStr("Add Presence Device?")), type: "bool", defaultValue: false, required: false, submitOnChange: true)
			state.presDevice = (Boolean)settings.presDevice ?: null

			input "weatherDevice", "capability.relativeHumidityMeasurement", title: imgTitle(getAppImg("i_t"), inputTitleStr("External Weather Devices?")), required: false, multiple: false, submitOnChange: true
		}

		Boolean devSelected = ((String)state.structures && ((Map)state.thermostats || (Map)state.protects || (Map)state.cameras || (Boolean)state.presDevice))
		if(isInstalled && devSelected) {
			section("<h3>Customize Device Names:</h3>") {
				String descStr
				if((Boolean)state.devNameOverride) {
					if((Boolean)state.custLabelUsed) {
						descStr = "• Custom Labels Are Active"
					}
					if((Boolean)state.useAltNames) {
						descStr = "• Using Location Name as Prefix is Active"
					}
				}
				String devDesc = descStr!=sNULL ? '\n'+descStr+'\n\n'+descriptions('d_ttm') : descriptions('d_ttc')
				href "devNamePage", title: imgTitle(getAppImg("device_name_icon.png"), inputTitleStr("Device Names")), description: devDesc, state:(!(Boolean)state.devNameOverride || ((Boolean)state.devNameOverride && ((Boolean)state.custLabelUsed || (Boolean)state.useAltNames))) ? "complete" : ""
			}
		}
	}
}

def reviewSetupPage() {
	return dynamicPage(name: "reviewSetupPage", title: "", install: true, uninstall: (Boolean)state.isInstalled) {
		section(sectionTitleStr("Device Setup Summary:")) {
			String t0 = getDevicesDesc()
			String str = t0 ?: ""
			paragraph title: (!(Boolean)state.isInstalled ? "Devices Pending Install:" : "Installed Devices:"), str
			paragraph '<p style="color: blue;">Tap <b>Done</b> to complete the install and create the devices selected</p>', state: "complete"
		}
	}
}

def devNamePage() {
	String pageLbl = (Boolean)state.isInstalled ? "Device Labels" : "Custom Device Labels"
	dynamicPage(name: "devNamePage", title: pageLbl, nextPage: "", install: false) {
		if((Boolean)settings.devNameOverride == null || (Boolean)state.devNameOverride == null) {
			state.devNameOverride = true
			settingUpdate("devNameOverride","true","bool")
		}
		Boolean overrideName = (state.devNameOverride == true)
		Boolean altName = ((Boolean)state.useAltNames == true)
		Boolean custName = ((Boolean)state.custLabelUsed == true)
		section(sectionTitleStr("Device Name Settings")) {
			input (name: "devNameOverride", type: "bool", title: inputTitleStr("App Overwrites Device Names?"), required: false, defaultValue: overrideName, submitOnChange: true )
			if(devNameOverride && !useCustDevNames) {
				input (name: "useAltNames", type: "bool", title: inputTitleStr("Use Location Name as Prefix?"), required: false, defaultValue: altName, submitOnChange: true, image: "" )
			}
			if(devNameOverride && !useAltNames) {
				input (name: "useCustDevNames", type: "bool", title: inputTitleStr("Assign Custom Names?"), required: false, defaultValue: custName, submitOnChange: true, image: "" )
			}

			state.devNameOverride = (Boolean)settings.devNameOverride ? true : false
			if((Boolean)state.devNameOverride) {
				state.useAltNames = (Boolean)settings.useAltNames ? true : false
				state.custLabelUsed = (Boolean)settings.useCustDevNames ? true : false
			} else {
				state.useAltNames = false
				state.custLabelUsed = false
			}
/*
			if((Boolean)state.custLabelUsed) {
				paragraph "Custom Labels Are Active", state: "complete"
			}
			if((Boolean)state.useAltNames) {
				paragraph "Using Location Name as Prefix is Active", state: "complete"
			}
*/
			//paragraph "Current Device Handler Names", image: ""
		}

		Boolean found = false
		if((Map)state.thermostats) {
			section (sectionTitleStr("Thermostat Device(s):")) {
				state.thermostats?.each { t ->
					found = true
					def d = getChildDevice(getNestTstatDni(t))
					deviceNameFunc(d, getNestTstatLabel(t.value, t.key), "tstat_${t?.key}_lbl", "thermostat")
				}
				state.vThermostats?.each { t ->
					found = true
					def d = getChildDevice(getNestvStatDni(t))
					deviceNameFunc(d, getNestVstatLabel(t.value, t.key), "vtstat_${t?.key}_lbl", "thermostat")
				}
			}
		}
		if(state.protects) {
			section (sectionTitleStr("Protect Device Names:")) {
				state.protects?.each { p ->
					found = true
					def d = getChildDevice(getNestProtDni(p))
					deviceNameFunc(d, getNestProtLabel(p.value, p.key), "prot_${p?.key}_lbl", "protect")
				}
			}
		}
		if(state.cameras) {
			section (sectionTitleStr("Camera Device Names:")) {
				state.cameras?.each { c ->
					found = true
					def d = getChildDevice(getNestCamDni(c))
					deviceNameFunc(d, getNestCamLabel(c.value, c.key), "cam_${c?.key}_lbl", "camera")
				}
			}
		}
		if((Boolean)state.presDevice) {
			section (sectionTitleStr("Presence Device Name:")) {
				found = true
				String pLbl = getNestPresLabel()
				String dni = getNestPresId()
				def d = getChildDevice(dni)
				deviceNameFunc(d, pLbl, "presDev_lbl", "presence")
			}
		}
		if(!found) {
			paragraph "<h3>No Devices Selected</h3>"
		}
		state.forceChildUpd = true
	}
 }

def deviceNameFunc(dev, String label, String inputStr, String devType) {
	String dstr = ""
	if(dev) {
		dstr += '<u>Found:</u> '+(String)dev.displayName
		if((String)dev.displayName != label) {
			String str1 = "\n\n<b>Name is not set to default.\nDefault name is:</b>"
			dstr += str1+'\n'+label
		}
	} else {
		dstr += '<b>New Name:</b>\n'+label
	}
	String dtyp = (Boolean)state.custLabelUsed ? "blank" : devType
	paragraph imgTitle(getAppImg(dtyp+'_icon.png'), dstr), state: "complete"
	//paragraph "${dstr}", state: "complete", image: ((Boolean)state.custLabelUsed) ? " " : getAppImg("${devType}_icon.png")
	if((Boolean)state.custLabelUsed) {
		input "${inputStr}", "text", title: imgTitle(getAppImg(devType+'_icon.png'), inputTitleStr('Custom name for '+label)), defaultValue: label, submitOnChange: true
	}
 }

String getAppNotifConfDesc() {
	String str = ""
	if(settings.phone || ((Boolean)settings.pushoverEnabled && settings.pushoverDevices)) {
		str += ((Boolean)settings.pushoverEnabled) ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
//		str += (settings.phone) ? "${str != "" ? "\n" : ""}Sending via: (SMS)" : ""

		if(str != "") {
			String t0 = ""
			t0 += (Boolean)settings.appApiIssuesMsg != false ? "\n • API CMD Failures" : ""
			t0 += (Boolean)settings.locPresChangeMsg != false ? "\n • Nest Home/Away Status Changes" : ""
			t0 += (Boolean)settings.camStreamNotifMsg != false ? "\n • Camera Stream Alerts" : ""
			t0 += (Boolean)settings.automationNotifMsg != false ? "\n • Automation Notifications" : ""
			if(t0 != "") {
				str += "\n\nAlerts:"
				str += "${t0}"
			}
		}
	}
	return str != "" ? str : sNULL
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
/*		section("Enable Text Messaging:") {
			input "phone", "phone", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Send SMS to Number\n(Optional)")), required: false, submitOnChange: true
		}*/
		section("Enable Notification Devices:") {
			input "pushoverEnabled", "bool", title: imgTitle(getAppImg("i_pu"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
			if((Boolean)settings.pushoverEnabled) {
				input "pushoverDevices", "capability.notification", title: imgTitle(getAppImg("i_pu"), inputTitleStr("Notification Device")), required: true, submitOnChange: true
			}else state.pushTested=false
		}
		if(settings.phone || ((Boolean)settings.pushoverEnabled && settings.pushoverDevices)) {
/*
			section("Notification Restrictions:") {
				def t1 = getNotifSchedDesc()
				href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : sNULL), image: getAppImg("restriction_icon.png")
			}
*/
			if( ((Boolean)settings.pushoverEnabled && settings.pushoverDevices) && !(Boolean)state.pushTested) {
				if(sendMsg("Info", 'Push Notification Test Successful. Notifications Enabled for '+(String)app.label, 0)) {
					state.pushTested = true
				}
			}
			section("Alerts:") {
				paragraph "Receive notifications when there are issues with the Nest API", state: "complete"
				input "appApiIssuesMsg", "bool", title: imgTitle(getAppImg("i_i"), inputTitleStr("Notify on API Issues?")), defaultValue: true, submitOnChange: true
				paragraph "Get notified when the Location changes from Home/Away", state: "complete"
				input "locPresChangeMsg",  "bool", title: imgTitle(getAppImg("i_pr"), inputTitleStr("Notify on Nest Home/Away changes?")), defaultValue: true, submitOnChange: true
				if(settings.cameras) {
					paragraph "Get notified on Camera streaming changes", state: "complete"
					input "camStreamNotifMsg", "bool", title: imgTitle(getAppImg("i_c"), inputTitleStr("Send Cam Streaming Alerts?")), required: false, defaultValue: true, submitOnChange: true
				}
				paragraph "Automation Notification Messages", state: "complete"
				input "automationNotifMsg",  "bool", title: imgTitle(getAppImg("i_i"), inputTitleStr("Automation Notifications?")), defaultValue: true, submitOnChange: true
			}
		}
	}
}

Boolean sendMsg(String msgType, String msg, Integer lvl, pushoverDev=null, sms=null) {
	String newMsg = msgType+': '+msg
	LogTrace('sendMsg '+lvl.toString()+' '+newMsg)
	Boolean retVal = false
	if(lvl == 1 && (Boolean)settings.appApiIssuesMsg == false) { return retVal }
	if(lvl == 2 && (Boolean)settings.locPresChangeMsg == false) { return retVal }
	if(lvl == 3 && (Boolean)settings.camStreamNotifMsg == false) { return retVal }
	if((lvl == 4 || lvl == 5) && (Boolean)settings.automationNotifMsg == false) { return retVal }
	def notifDev = pushoverDev ?: settings.pushoverDevices
	if(notifDev && (Boolean)settings.pushoverEnabled) {
		retVal = true
		notifDev*.deviceNotification(newMsg)
	}
/*	String thephone = sms ? sms.toString() : settings.phone ? settings.phone?.toString() : ""
	if(thephone) {
		retVal = true
		String t0 = newMsg.take(140)
		sendSms(thephone, t0)
	}*/
	return retVal
}

def debugPrefPage() {
	dynamicPage(name: "debugPrefPage", install: false) {
		section (sectionTitleStr("Application Logs")) {
			input ("dbgAppndName", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Show App/Device Name on all Log Entries?")), required: false, defaultValue: false, submitOnChange: true)
			input ("appDebug", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Show ${app.name} Logs in the IDE?")), required: false, defaultValue: false, submitOnChange: true)
			if((Boolean)settings.appDebug) {
				input ("advAppDebug", "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose (Trace) Logs?")), required: false, defaultValue: false, submitOnChange: true)
				input ("showDataChgdLogs", "bool", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Show API Data Changed in Logs?")), required: false, defaultValue: false, submitOnChange: true)
			} else {
				settingUpdate("advAppDebug", "false", "bool")
				settingUpdate("showDataChgdLogs", "false", "bool")
			}
		}
//		section (sectionTitleStr("Reset Application Data")) {
//			input (name: "resetAllData", type: "bool", title: imgTitle(getAppImg("i_r"), inputTitleStr("Reset Application Data?")), required: false, defaultValue: false, submitOnChange: true)
//		}
		if((Boolean)settings.appDebug) {
			if(getTimestampVal("debugEnableDt") == sNULL) { updTimestampMap("debugEnableDt", getDtNow()) }
		} else { updTimestampMap("debugEnableDt") }
		state.needChildUpd = true

		section("App Info") {
			paragraph imgTitle(getAppImg("progress_bar.png"), "Current State Usage:\n${getStateSizePerc()}% (${getStateSize()} bytes)"), required: true, state: (getStateSizePerc() <= 70 ? "complete" : sNULL)
			if((Boolean)state.isInstalled && (String)state.structures && ((Map)state.thermostats || (Map)state.protects || (Map)state.cameras)) {
				input "enDiagWebPage", "bool", title: imgTitle(getAppImg("i_d"), inputTitleStr("Enable Diagnostic Web Page?")), required: false, defaultValue: false, submitOnChange: true
/*
//device won't be created for a while so cannot do this now
				if((Boolean)settings.enDiagWebPage) {
					def t0 = getRemDiagApp()
					String t1 = t0.getAppEndpointUrl("diagHome")
					href url: t1, style:"external", title:"NST Diagnostic Web Page", description:"Tap to view", required: true, state: "complete", image: getAppImg("web_icon.png")
				}
*/
			}
		}
		if(getDevOpt()) {
			//settingUpdate("enDiagWebPage","true", "bool")
		}
		if((Boolean)settings.enDiagWebPage) {
			section("How's Does Log Collection Work:", hideable: true, hidden: true) {
				paragraph title: "How will the log collection work?", "When logs are enabled this App will create a child diagnostic app to store your logs which you can view under the diagnostics web page or share the url with the developer for remote troubleshooting.\n\n Turn off to remove the diag app and all data."
			}
			section("Log Collection:") {
/*
				def formatVal = settings.useMilitaryTime ? "MMM d, yyyy - HH:mm:ss" : "MMM d, yyyy - h:mm:ss a"
				SimpleDateFormat tf = new SimpleDateFormat(formatVal)
				if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
*/
				paragraph "Logging will automatically turn off in 48 hours and all logs will be purged."
				//input ("showDataChgdLogs", "bool", title: imgTitle(getAppImg("i_sw"), inputTitleStr("Show API Data Changed in Logs?")), required: false, defaultValue: false, submitOnChange: true)
				input ("enRemDiagLogging", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Enable Log Collection?")), required: false, defaultValue: ((Boolean)state.enRemDiagLogging ?: false), submitOnChange: true)
				if((Boolean)state.enRemDiagLogging) {
					String str = "Press Done/Save all the way back to the main app page to allow the Diagnostic App to Install"
					paragraph str, required: true, state: "complete"
				}
			}
		}
		diagLogProcChange((Boolean)settings.enDiagWebPage)

	}
}


def getRemDiagApp() {
	if((Boolean)settings.enDiagWebPage) {
		def remDiagApp = getChildApps()?.find { it?.getAutomationType() == "remDiag" && it?.name == "NST Diagnostics" }
		if(remDiagApp) {
			//if(remDiagApp?.label != getRemDiagAppChildLabel()) { remDiagApp?.updateLabel(getRemDiagAppChildLabel()) }
			return remDiagApp
		}
		diagLogProcChange((Boolean)settings.enDiagWebPage)
	}
	return null
}

private void diagLogProcChange(Boolean setOn) {
	log.trace "diagLogProcChange($setOn)"
	Boolean doInit = false
	String msg = "Remote Diagnostic Logs "

	Boolean  mysetOn = (setOn && (Boolean)settings.enDiagWebPage && (Boolean)settings.enRemDiagLogging) ? true : false
	//log.trace "state: ${(Boolean)state.enRemDiagLogging}   time:  ${getTimestampVal("remDiagLogActivatedDt")}"
	if(mysetOn) {
		if(!(Boolean)state.enRemDiagLogging && getTimestampVal("remDiagLogActivatedDt") == sNULL) {
			msg += "activated"
			doInit = true
			updTimestampMap("remDiagLogActivatedDt", getDtNow())
			state.enRemDiagLogging = true
			updTimestampMap("remDiagDataSentDt", getDtNow()) // allow us some time for child to start
		}
	} else {
		if(getTimestampVal("remDiagLogActivatedDt") != sNULL || (Boolean)state.enRemDiagLogging) {
			msg += "deactivated"
			settingUpdate("enRemDiagLogging", "false","bool")
			state.enRemDiagLogging = false
			updTimestampMap("remDiagLogActivatedDt")
			atomicState.remDiagLogDataStore = []
			doInit = true
		}
	}

	if(  ((Boolean)state.remDiagAppAvailable && !(Boolean)settings.enDiagWebPage) ||
		((Boolean)state.remDiagAppAvaiable == false && (Boolean)settings.enDiagWebPage) ) {
		initRemDiagApp() // create or delete as needed
	}

	if(doInit) {
		log.trace "diagLogProcChange: doInit"
		def kdata = getState()?.findAll { (it?.key in ["remDiagLogDataStore" /* , "remDiagDataSentDt"*/  ]) }
		kdata.each { kitem ->
			state.remove(kitem.key.toString())
		}

		LogAction(msg, "info", true)
		if(!(Boolean)state.enRemDiagLogging) { //when turning off, tell automations; turn on - user does done to this app
			def cApps = getChildApps()?.findAll { !(it?.getAutomationType() == "remDiag") }
			if(cApps) {
				cApps?.sort()?.each { chld ->
					chld?.updated()
				}
			}
			devs = app.getChildDevices()
			devs?.each { dev ->
				dev.stateRemove("enRemDiagLogging")
			}
		}
		state.forceChildUpd = true
		updTimestampMap("lastAnalyticUpdDt")
	}
}

Integer getRemDiagActSec() { return getTimeSeconds("remDiagLogActivatedDt", 100000, "getRemDiagActSec") }
Integer getLastRemDiagSentSec() { return getTimeSeconds("remDiagDataSentDt", 1000, "getLastRemDiagSentSec") }


static Boolean getDevOpt() {
	return true
//	appSettings?.devOpt.toString() == "true" ? true : false
}

/******************************************************************************
 |						PAGE TEXT DESCRIPTION METHODS						|
 *******************************************************************************/
String getDevicesDesc(Boolean startNewLine=true) {
	Boolean pDev = (List)settings.thermostats || (List)settings.protects || (List)settings.cameras
	Boolean vDev = (List)settings.vThermostats || (Boolean)settings.presDevice
	String str = ""
	str += pDev ? "${startNewLine ? "\n" : ""}<b>Physical Devices:</b>" : ""
	str += (List)settings.thermostats ? "\n • <i>[${((List)settings.thermostats)?.size()}] Thermostat${(((List)settings.thermostats)?.size() > 1) ? "s" : ""}</i>" : ""
	str += (List)settings.protects ? "\n • <i>[${((List)settings.protects)?.size()}] Protect${(((List)settings.protects)?.size() > 1) ? "s" : ""}</i>" : ""
	str += (List)settings.cameras ? "\n • <i>[${((List)settings.cameras)?.size()}] Camera${(((List)settings.cameras)?.size() > 1) ? "s" : ""}</i>" : ""

	str += vDev ? "${pDev ? "\n" : ""}\n<b>Virtual Devices:</b>" : ""
	str += state.vThermostats ? "\n • [${state.vThermostats?.size()}] Virtual Thermostat${(state.vThermostats?.size() > 1) ? "s" : ""}" : ""
	str += (Boolean)settings.presDevice ? "\n • <i>Presence Device</i>" : ""
	str += settings.weatherDevice ? "\n • <i>Weather Device Configured</i>" : ""
	str += (!(List)settings.thermostats && !(List)settings.protects && !(List)settings.cameras && !(Boolean)settings.presDevice) ? "\n • <i>No Devices Selected</i>" : ""
	return (str != "") ? str : sNULL
}

String getAppDebugDesc() {
	String str = ""
	str += isAppDebug() ? "App Debug: (${debugStatus()})${(Boolean)settings.advAppDebug ? "(Trace)" : ""}" : ""
	str += (Boolean)settings.showDataChgdLogs ? "${str ? "\n" : ""}Log API Changes: (${(Boolean)settings.showDataChgdLogs ? "True" : "False"})" : ""
	str += getRemDiagDesc() ? "${str ? "\n" : ""}${getRemDiagDesc()}" : ""
	return (str != "") ? str : sNULL
}


String getRemDiagDesc() {
	String str = ""
	str += (Boolean)settings.enDiagWebPage ? "Web Page: (${(Boolean)settings.enDiagWebPage})" : ""
	if((Boolean)settings.enRemDiagLogging) {
		str += "\nLog Collection: (${(Boolean)settings.enRemDiagLogging})"
		String diagTime = (getTimestampVal("remDiagLogActivatedDt") != sNULL) ? "\n• Will Disable in:\n  └ ${getDiagLogTimeRemaining()}" : "\n no time remaining found"
		str += diagTime
	}
	return (str != "") ? str : sNULL
}


/******************************************************************************
 *								NEST LOGIN PAGES							*
 *******************************************************************************/
def nestLoginPrefPage () {
	if(getNestAuthToken()==sNULL) {
		return authPage()
	} else {
		return dynamicPage(name: "nestLoginPrefPage", title: "<h2>Nest Authorization Page</h2>", nextPage: getNestAuthToken() ? "" : "authPage", install: false) {
			String t0=getTimestampVal("authTokenCreatedDt")
			updTimestampMap("authTokenCreatedDt", (t0 ?: getDtNow()))
			section() {
				paragraph "<b>Date Authorized:</b>\n• ${getTimestampVal("authTokenCreatedDt")}".toString(), state: "complete"
				if(getTimestampVal("lastDevDataUpd")) {
					paragraph "<b>Last API Connection:</b>\n• ${getTimestampVal("lastDevDataUpd")}".toString()
				}
			}
			section(sectionTitleStr("Revoke Authorization Reset:")) {
				href "nestTokenResetPage", title: imgTitle(getAppImg("i_r"), inputTitleStr("Log Out and Reset Nest Token")), description: "<i>Tap to Reset Nest Token</i>", required: true, state: sNULL
			}
		}
	}
}

def nestTokenResetPage() {
	return dynamicPage(name: "nestTokenResetPage", install: false) {
		section (sectionTitleStr("Resetting Nest Token")) {
			//revokeNestToken()
			paragraph "Token Reset Complete...", state: "complete"
			paragraph "Press Done/Save to return to Login page"
		}
	}
}

static String autoAppName()	{ return "NST Automations" }

def automationsPage() {
	return dynamicPage(name: "automationsPage", title: "Installed Automations", nextPage: !parent ? "" : "automationsPage", install: false) {
		def autoApp = getChildApps()?.find { it?.name == autoAppName() || it?.name == "NST Graphs" || it?.name == "NST Diagnostics"}
		Boolean autoAppInst = isAutoAppInst()
		if(autoApp) { /*Nothing to add here yet*/ }
		else {
			section("") {
				paragraph "You haven't created any Automations yet!\nTap Create New Automation to get Started"
			}
		}
		section("") {
			app(name: "autoApp", appName: autoAppName(), namespace: "tonesto7", multiple: true, title: imgTitle(getAppImg("nst_automations_5.png"), inputTitleStr("Create New Automation (NST)")))
			app(name: "autoApp", appName: "NST Graphs", namespace: "tonesto7", multiple: false, title: imgTitle(getAppImg("i_g"), inputTitleStr("Create Charts Automation")))
			app(name: "autoApp", appName: "NST Diagnostics", namespace: "tonesto7", multiple: false, title: imgTitle(getAppImg("i_d"), inputTitleStr("Diagnostics Automation")))
		}
		if(autoAppInst) {
			section("Automation Details:") {
				def schEn = getChildApps()?.findAll { (!(it.getAutomationType() in ["nMode", "watchDog", "chart", "remDiag" ]) && it?.getActiveScheduleState()) }
				if(schEn?.size()) {
					href "automationSchedulePage", title: imgTitle(getAppImg("i_s"), inputTitleStr("View Automation Schedule(s)")), description: ""
				}
			}
			section("Advanced Options: (Tap + to Show)		", hideable: true, hidden: true) {
/*
				def descStr = ""
				descStr += (settings.locDesiredCoolTemp || settings.locDesiredHeatTemp) ? "Comfort Settings:" : ""
				descStr += settings.locDesiredHeatTemp ? "\n • Desired Heat Temp: (${settings.locDesiredHeatTemp}${tUnitStr()})" : ""
				descStr += settings.locDesiredCoolTemp ? "\n • Desired Cool Temp: (${settings.locDesiredCoolTemp}${tUnitStr()})" : ""
				descStr += (settings.locDesiredComfortDewpointMax) ? "${(settings.locDesiredCoolTemp || settings.locDesiredHeatTemp) ? "\n\n" : ""}Dew Point:" : ""
				descStr += settings.locDesiredComfortDewpointMax ? "\n • Max Dew Point: (${settings.locDesiredComfortDewpointMax}${tUnitStr()})" : ""
				descStr += "${(settings.locDesiredCoolTemp || settings.locDesiredHeatTemp) ? "\n\n" : ""}${getSafetyValuesDesc()}" ?: ""
				def prefDesc = (descStr != "") ? descStr+'\n\n'+descriptions('d_ttm') : descriptions('d_ttc')
				href "automationGlobalPrefsPage", title: "Global Automation Preferences", description: prefDesc, state: (descStr != "" ? "complete" : sNULL), image: getAppImg("global_prefs_icon.png")
*/
				input "disableAllAutomations", "bool", title: "Disable All Automations?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("disable_icon2.png")
				if(state.disableAllAutomations == false && settings.disableAllAutomations) {
					toggleAllAutomations(true)

				} else if(state.disableAllAutomations && !settings.disableAllAutomations) {
					toggleAllAutomations(true)
				}
				state.disableAllAutomations = settings.disableAllAutomations == true ? true : false
			}
		}
		state.ok2InstallAutoFlag = true
	}
}

def automationSchedulePage() {
	dynamicPage(name: "automationSchedulePage", title: "View Schedule Data..", uninstall: false) {
		section() {
			String str = ""
			def tz = TimeZone.getTimeZone(location.timeZone.ID)
			def sunTimes = app.getSunriseAndSunset()
			def sunsetT = Date.parse("E MMM dd HH:mm:ss z yyyy", sunTimes.sunset.toString()).format('h:mm a', tz)
			def sunriseT = Date.parse("E MMM dd HH:mm:ss z yyyy", sunTimes.sunrise.toString()).format('h:mm a', tz)
			str += "Mode: (${location?.mode})"
			str += "\nSunrise: (${sunriseT})"
			str += "\nSunset: (${sunsetT})"
			paragraph paraTitleStr("Hub Location Info:"), state: "complete"
			paragraph sectionTitleStr(str), state: "complete"
		}
		Integer schSize = 0
		getChildApps()?.each { capp ->
			Map schInfo = capp.getScheduleDesc()
			if(schInfo?.size()) {
				Integer curSch = capp.getCurrentSchedule()
				schSize = schSize+1
				schInfo?.each { schItem ->
					section("${capp.label}") {
						def schNum = schItem?.key
						String schDesc = schItem?.value
						Boolean schInUse = (curSch?.toInteger() == schNum?.toInteger()) ? true : false
						if(schNum && schDesc) {
							paragraph schDesc, state: schInUse ? "complete" : ""
						}
					}
				}
			}
		}
		if(schSize < 1) {
			section("") {
				paragraph "There is No Schedule Data to Display"
			}
		}
	}
}

private void toggleAllAutomations(Boolean upd = false) {
	Boolean t0 = settings.disableAllAutomations == true ? true : false
	state.disableAllAutomations = t0
	String disStr = !t0 ? "Returning control to" : "Disabling"
	def cApps = getChildApps()
	cApps.each { ca ->
		LogAction("toggleAllAutomations: ${disStr} automation ${ca.label}", "info", true)
		ca?.setAutomationStatus(upd)
	}
}


Boolean isAutoAppInst() {
	Integer chldCnt = 0
	childApps?.each { cApp ->
		chldCnt = chldCnt + 1
	}
	return (chldCnt > 0) ? true : false
}

String getInstAutoTypesDesc() {
	Map dat = ["nestMode":0, "watchDog":0, "chart": 0, "remDiag":0, "disabled":0, "schMot":["tSched":0, "remSen":0, "fanCtrl":0, "fanCirc":0, "conWat":0, "extTmp":0, "leakWat":0, "humCtrl":0 ]]
	List disItems = []
	Map nItems = [:]
	List schMotItems = []
	childApps?.each { a ->
		String type
//		String ver
		def dis
		try {
			type = (String)a?.getAutomationType()
			dis = (Boolean)a?.getIsAutomationDisabled()
//			ver = (String)a?.appVersion()
		}
		catch(ex) {
			dis = null
//			ver = sNULL
			type = "old"
		}
		if(dis) {
			disItems.push((String)a.label)
			dat["disabled"] = dat["disabled"] ? dat["disabled"]+1 : 1
		} else {
			String tt1 = ""
			Boolean clean = false
			switch(type) {
				case "nMode":
					tt1 = "nestMode"
					clean = true
					break
				case "schMot":
					List ai
					try {
						ai = a?.getAutomationsInstalled()
						schMotItems += (List)a?.getSchMotConfigDesc(true)
					}
					catch (Exception e) {
						log.error "BAD Automation file ${a?.label?.toString()}, please RE-INSTALL automation file"
					}
					if(ai) {
						ai?.each { aut ->
							aut?.each { it2 ->
								if((String)it2.key == "schMot") {
									it2?.value?.each {
										nItems[it] = nItems[it] ? nItems[it]+1 : 1
									}
								}
							}
						}
					}
					dat["schMot"] = nItems
					break
				case "watchDog":
					tt1 = "watchDog"
					clean = true
					break
				case "remDiag":
					tt1 = "remDiag"
					clean = true
					break
				case "chart":
					tt1 = "chart"
					clean = true
					break
				default:
					LogAction("Deleting Unknown Automation (${a?.id})", "warn", true)
					deleteChildApp(a.id)
					updTimestampMap("lastAnalyticUpdDt")
					break
			}
			if(clean) {
				dat["${tt1}"] = dat["${tt1}"] ? dat["${tt1}"]+1 : 1
				if(dat."${tt1}" > 1) {
					dat."${tt1}" = dat."${tt1}" - 1
					LogAction("Deleting Extra ${tt1} (${a?.id})", "warn", true)
					deleteChildApp(a.id)
					updTimestampMap("lastAnalyticUpdDt")
				}
			}
		}
	}
	state.installedAutomations = dat

	String str = ""
	str += (dat.watchDog > 0 || dat?.chart > 0 || dat?.nestMode > 0 || dat?.schMot || dat?.remDiag || dat?.disabled > 0) ? "Installed Automations:" : ""
	str += (dat.watchDog > 0) ? "\n• Watchdog (Active)" : ""
	str += (dat.chart > 0) ? "\n• Chart (Active)" : ""
	str += (dat.remDiag > 0) ? "\n• Remote Diags (Active)" : ""
	str += (dat.nestMode > 0) ? ((dat?.nestMode > 1) ? "\n• Nest Home/Away (${dat?.nestMode})" : "\n• Nest Home/Away (Active)") : ""
	def sch = dat.schMot.findAll { it?.value > 0}
	str += (sch?.size()) ? "\n• Thermostat (${sch?.size()})" : ""
	Integer scii = 1
	def newList = schMotItems?.unique()
	newList?.sort()?.each { sci ->
		str += "${scii == newList?.size() ? "\n  └" : "\n  ├"} $sci"
		scii = scii+1
	}
	str += (disItems?.size() > 0) ? "\n• Disabled: (${disItems?.size()})" : ""
	return (str != "") ? str : sNULL
}

/******************************************************************************
 *#########################	NATIVE APP METHODS ############################*
 ******************************************************************************/
void installed() {
	LogAction("Installed with settings: ${settings}", "debug", true)
	initialize()
}

void updated() {
	LogAction("${app.label} Updated...with settings: ${settings}", "debug", true)
	if((Boolean)state.needToFinalize == true) { LogAction("Skipping updated() as auth change in-progress", "warn", true); return }
	initialize()
	state.lastUpdatedDt = getDtNow()
}

void uninstalled() {
	LogTrace("uninstalled")
	try {
		state.access_token = null
		//Revokes Nest Auth Token
		//revokeNestToken()
		addRemoveDevices(true)
	} catch (ex) {
		log.error "uninstalled Exception: ${ex?.message}"
	}
}

void initialize() {
	LogAction("initialize", "debug", true)
	restStreamHandler(true, "initialize()", false)
	unschedule()
	unsubscribe()
	state.pollingOn = false
	state.restStreamingOn = false
	state.streamPolling = false
	atomicState.diagRunInOn = false
	atomicState.workQrunInActive = false
	state.pollBlocked = false
	state.remove("pollBlockedReason")
	stateCleanup()

	if(getTimestampVal("debugEnableDt") == sNULL) {
		updTimestampMap("debugEnableDt", getDtNow())
		settingUpdate("appDebug", "true",  "bool")
		runIn(600, logsOff)
	} else {
		if((Boolean)settings.appDebug || (Boolean)settings.advAppDebug || (Boolean)settings.showDataChgdLogs) { runIn(1800, logsOff) }
	}

	// force child update on next poll
	updTimestampMap("lastChildUpdDt")
	updTimestampMap("lastChildForceUpdDt")
	updTimestampMap("lastForcePoll")

	if((String)settings.structures && (String)state.structures && !(String)state.structureName) {
		Map structs = getNestStructures()
		if(structs && structs["${(String)state.structures}"]) {
			state.structureName = (String)structs[(String)state.structures]
		}
	}
	reInitBuiltins() // get watchDog to release devices
	runIn(4, "initialize_Part1", [overwrite: true])  // give time for child apps to run
}

void initialize_Part1() {
	LogTrace("initialize_Part1")
	if(!addRemoveDevices()) {
		atomicState.cmdQlist = []
	}
	if((List)settings.thermostats || (List)settings.protects || (List)settings.cameras || (Boolean)settings.presDevice) {
		state.isInstalled = true
	} else { state.isInstalled = false }
	subscriber()
	runIn(10, "finishUp", [overwrite: true])  // give time for devices to initialize

}

void finishUp() {
	LogTrace("finishUp")
	if((Boolean)state.isInstalled) { createSavedNest() }
	getChildApps()?.sort()?.each { chld ->
		chld?.updated()
	}
	setPollingState()
}

void logsOff() {
	log.warn "${app.label} debug logging disabled...${getDebugLogsOnSec()}"
	settingUpdate("appDebug", "false",  "bool")
	settingUpdate("advAppDebug", "false", "bool")
	settingUpdate("showDataChgdLogs", "false", "bool")
	updTimestampMap("debugEnableDt")
}

Integer getDebugLogsOnSec() { return getTimeSeconds("debugEnableDt", 0, "getDebugLogsOnSec") }

void reInitBuiltins() {
	initWatchdogApp()
	initNestModeApp() // this just removes extras
	initBuiltin("initChart") // this just removes extras and lets it release device subscriptions
	initRemDiagApp()
}

void initNestModeApp() {
	initBuiltin("initNestModeApp")
}

void initWatchdogApp() {
	initBuiltin("initWatchdogApp")
}

void initRemDiagApp() {
	log.trace "initRemDiagApp"
	initBuiltin("initRemDiagApp")
}

void initBuiltin(String btype) {
	LogTrace("initBuiltin(${btype})")
	Boolean keepApp = false
	Boolean createApp = false
	String autoStr = ""
	switch (btype) {
		case "initNestModeApp":
			if(automationNestModeEnabled()) {
				keepApp = true
				autoStr = "nMode"
			}
			break
		case "initWatchdogApp":
			Integer t0 = settings.thermostats?.size()
			Integer t1 = settings.cameras?.size()
			if((Boolean)state.isInstalled && (t0>0 || t1>0)) {
				keepApp = true
				createApp = true
			}
			autoStr = "watchDog"
			break
		case "initRemDiagApp":
			if((Boolean)settings.enDiagWebPage) {
				keepApp = true
				createApp = true
				remDiagAppAvail(true)
			} else {
				settingUpdate("enRemDiagLogging", "false","bool")
				remDiagAppAvail(false)
				state.enRemDiagLogging = false
				updTimestampMap("remDiagLogActivatedDt")
			}
			autoStr = "remDiag"
			break
		case "initChart":
			autoStr = "chart"
			keepApp = true
			break
		default:
			LogAction("initBuiltin BAD btype ${btype}", "warn", true)
			break
	}
	//if(isAppLiteMode()) { keepApp = false }
	if(autoStr) {
		def mynestApp = getChildApps()?.findAll { it?.getAutomationType() == autoStr }
		if(createApp && mynestApp?.size() < 1 /* && btype != "initNestModeApp" && btype != "chart" */) {
			LogAction("Installing ${autoStr}", "info", true)
			updTimestampMap("lastAnalyticUpdDt")
			try {
				if(btype == "initRemDiagApp") {
					addChildApp("tonesto7", "NST Diagnostics", getRemDiagAppChildLabel(), null) //[settings:[remDiagFlag:["type":"bool", "value":true]]])
				}
				if(btype == "initWatchdogApp") {
					addChildApp("tonesto7", autoAppName(), getWatDogAppChildLabel(), null)    //[state:["watchDogFlag":[type:"bool", value:true]], state:["autoTyp":[type:"string", value:"watchDog"]]])
				}
			} catch (ex) {
				Logger("WatchDog create failure", "error")
				//appUpdateNotify(true, "automation")
			}
		} else if(mynestApp?.size() >= 1) {
			Integer cnt = 1
			mynestApp?.each { chld ->
				if(keepApp && cnt == 1) {
					LogTrace("initBuiltin: Running Update Command on ${autoStr}")
					chld.updated()
				} else if(!keepApp || cnt > 1) {
					String slbl = keepApp ? "warn" : "info"
					LogAction("initBuiltin: Deleting ${keepApp ? "Extra " : ""}${autoStr} (${chld?.id})", slbl, true)
					deleteChildApp(chld.id)
					updTimestampMap("lastAnalyticUpdDt")
				}
				cnt = cnt+1
			}
		}
	}
}

private String getWatDogAppChildLabel()		{ return (String)location.name+' Watchdog' }
private String getRemDiagAppChildLabel()	{ return 'NST Location '+(String)location.name+' Diagnostics' }

def subscriber() { }

private adj_temp(tempF) {
	if(getObjType(tempF) in ["List", "ArrayList"]) {
		LogTrace("adj_temp: error temp ${tempF} is list")
	}
	if(getTemperatureScale() == "C") {
		return (tempF - 32) * (5 / 9) as Double
	} else {
		return tempF
	}
}

void setPollingState() {
	if(!(Map)state.thermostats && !(Map)state.protects && !(Map)state.cameras && !(Boolean)state.presDevice) {
		LogAction("No Devices are Installed | Polling is DISABLED", "info", true)
		unschedule("poll")
		state.pollingOn = false
		state.streamPolling = false
	} else {
		if(getNestAuthToken()==sNULL) {
			state.pollingOn = false
		}
		if(!state.pollingOn && getNestAuthToken()!=sNULL) {
			//LogAction("Polling is ACTIVE", "info", true)
			state.pollingOn = true
			Integer pollTime = DevPoll() as Integer
			Integer pollStrTime = StrPoll() as Integer
			Integer theMax = 60
			if(restEnabled() && (Boolean)state.restStreamingOn) {
				theMax = 300   // 5 minute poll checks
				state.streamPolling = true
			}
			pollTime = Math.max(pollTime, theMax)
			pollStrTime = Math.max(pollStrTime, theMax)
			Integer timgcd = gcd([pollTime, pollStrTime])
			Random random = new Random()
			Integer random_int = random.nextInt(60)
			timgcd = (timgcd / 60) < 1 ? 1 : timgcd / 60
			Integer random_dint = random.nextInt(timgcd)
			LogTrace("Next POLL scheduled (${random_int} ${random_dint}/${timgcd} * * * ?)")
			// this runs every timgcd minutes
			schedule("${random_int} ${random_dint}/${timgcd} * * * ?", poll)
			Integer timChk = state.streamPolling ? 1200 : 240
			if(!getTimestampVal("lastDevDataUpd") || getLastDevPollSec() > timChk) {
				poll(true)
			} else {
				runIn(30, "pollFollow", [overwrite: true])
			}
		}
	}
}

void startStopStream() {
	// log.trace "startStopStream"
	if((!restEnabled()) && !(Boolean)state.restStreamingOn) {
		return
	}
	if(restEnabled() && (Boolean)state.restStreamingOn) {
		runIn(30, "restStreamCheck", [overwrite: true])
		return
	}
	if(restEnabled() && !(Boolean)state.restStreamingOn) {
		restStreamHandler(false, "startStopStream(start stream)")
		runIn(30, "restStreamCheck", [overwrite: true])
	}
}

def getStreamDevice() {
	return getChildDevice(getEventDeviceDni())
}

void restStreamHandler(Boolean close = false, String src, Boolean resetPoll=true) {
	LogAction("restStreamHandler(close: ${close}, src: ${src}), resetPoll: ${resetPoll}", "trace", true)
	def dev = getStreamDevice()
	if(!dev) {
		state.restStreamingOn = false
		//return
	} else {
		if(close) {
			state.restStreamingOn = false
			dev?.streamStop()
			if(state.streamPolling && resetPoll) {
				resetPolling()
			}
		} else {
			if(getNestAuthToken()==sNULL) {
				LogTrace("restStreamHandler: No authToken")
				state.restStreamingOn = false
				return
			}
			dev?.blockStreaming(!restEnabled())
			dev?.streamStart()
		}
	}
}

/*
def setRestActive(val) {
	// LogAction("setRestActive($val)", "trace", true)
	if(val == false) {
		state.restStreamingOn = false
		resetPolling()
	}
}
*/

void restStreamCheck() {
	LogTrace("restStreamCheck")
	def streamDev = getStreamDevice()
	if(!streamDev) {
		state.restStreamingOn = false
		resetPolling()
		return
	}
	if(getNestAuthToken()==sNULL) {
		//LogAction("restStreamCheck: NestAuthToken Not Found!", "warn", false)
		//return
	}
}

private static Integer gcd(Integer a, Integer b) {
	while (b > 0) {
		Integer temp = b
		b = a % b
		a = temp
	}
	return a
}

private static Integer gcd(List<Integer>input = []) {
	Integer result = input[0]
	for (Integer i = 1; i < input.size; i++) {
		result = gcd(result, (Integer)input[i])
	}
	return result
}

void refresh(child = null) {
	String devId = !child?.device?.deviceNetworkId ? child?.toString() : child.device.deviceNetworkId.toString()
	//LogAction("Refresh Called by Device: (${child?.device?.displayName}", "debug", false)
	Boolean a=sendNestCmd((String)state.structures, "poll", "poll", 0, devId)
}

/************************************************************************************************
 |								API/Device Polling Methods										|
 *************************************************************************************************/

void pollFollow() { poll() }

void poll(Boolean force = false, String type = sNULL) {
	if(isPollAllowed()) {
		if(force == true) {
			forcedPoll(type)
			finishPoll()
			return
		}
		Integer pollTime = DevPoll()
		if(restEnabled() && (Boolean)state.restStreamingOn) {
			pollTime = 300
		}
		Integer pollTimeout = pollTime*4 + 85
		Integer lastCheckin = getLastHeardFromNestSec()
		if(lastCheckin > pollTimeout) {
			if(restEnabled() && (Boolean)state.restStreamingOn) {
				if(lastCheckin < 10000) {
					LogAction("We have not heard from Nest Stream in (${lastCheckin}sec.) | Stopping and Restarting Stream", "warn", true)
				}
				restStreamHandler(true, "poll", false)  // close the stream if we have not heard from it in a while
				//state.restStreamingOn = false
			}
		}

		if(state.streamPolling && (!restEnabled() || !(Boolean)state.restStreamingOn)) {	// return to normal polling
			resetPolling()
			return
		}

		if(restEnabled() && (Boolean)state.restStreamingOn) {
			LogTrace("Polling Skipped because Rest Streaming is ON")
			if(!state.streamPolling) {	// set to stream polling
				resetPolling()
				return
			}
			finishPoll()
			return
		}
		runIn(5,"startStopStream", [overwrite: true])

		Boolean okStruct = ok2PollStruct()
		Boolean okDevice = ok2PollDevice()
		Boolean okMeta = ok2PollMetaData()
		Boolean meta = false
		Boolean dev = false
		Boolean str = false
		if(!okDevice && !okStruct && !(getLastHeardFromNestSec() > pollTimeout*3)) {
			LogAction("Skipping Poll - Devices Data Updated: (${getLastDevPollSec()}sec) ago | Structure Data Updated: (${getLastStrPollSec()}sec) ago", "info", true)
		}
		else {
			String sstr = ""
			if(okStruct) {
				sstr += "Structure Data (Last Updated: ${getLastStrPollSec()} seconds ago)"
				str = getApiData("str")
			}
			if(okDevice) {
				sstr += sstr != "" ? " | " : ""
				sstr += "Device Data (Last Updated: ${getLastDevPollSec()} seconds ago)"
				dev = getApiData("dev")
			}
			if(okMeta) {
				sstr += sstr != "" ? " | " : ""
				sstr += "Meta Data (Last Updated: ${getLastMetaPollSec()} seconds ago)"
				meta = getApiData("meta")
			}
			if(sstr != "") { LogAction("Gathering Latest Nest ${sstr}", "info", true) }
		}
		finishPoll(str, dev)
	}
}

void finishPoll(Boolean str=false, Boolean dev=false) {
	//LogTrace("finishPoll($str, $dev) received")
	if((Boolean)state.pollBlocked) {
		LogAction("Polling BLOCKED | Reason: (${(String)state.pollBlockedReason})", "trace", true)
		if(getLastAnyCmdSentSeconds() > 75) { // if poll is blocked and we have not sent a command recently, try to kick the queues
			schedNextWorkQ()
		}
		return
	}
	if(getLastChildForceUpdSec() > (15*60)-2) { // if nest goes silent (no changes coming back); force all devices to get an update so they can check health
		state.forceChildUpd = true
	}
	if(dev || str || (Boolean)state.forceChildUpd || (Boolean)state.needChildUpd) { updateChildData() }
	apiIssueNotify()
	if((Boolean)state.enRemDiagLogging && (Boolean)settings.enRemDiagLogging) {
		Boolean a=saveLogtoRemDiagStore("", "", "", true) // force flush of remote logs
	}
}

void resetPolling() {
	state.pollingOn = false
	state.streamPolling = false
	unschedule("poll")
	unschedule("finishPoll")
	unschedule("postCmd")
	unschedule("pollFollow")
	setPollingState()		// will call poll
}

void schedFinishPoll(Boolean devChg) {
	finishPoll(false, devChg)
}

void forcedPoll(String type = sNULL) {
	LogTrace("forcedPoll($type) received")
	Integer lastFrcdPoll = getLastForcedPollSec()
	Integer pollWaitVal = refreshWait()
	pollWaitVal = Math.max(pollWaitVal, 10)

	if(lastFrcdPoll > pollWaitVal) { // This limits manual forces to 10 seconds or more
		updTimestampMap("lastForcePoll", getDtNow())
		atomicState.workQrunInActive = false
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		cmdProcState(false)

		LogAction("Last Forced Poll was (${lastFrcdPoll} seconds) ago.", "info", true)
		String str = "Gathering Latest Nest "
		if(type == "dev" || !type) {
			LogAction("${str}Device Data (forcedPoll)", "info", true)
			getApiData("dev")
		}
		if(type == "str" || !type) {
			LogAction("${str}Structure Data (forcedPoll)", "info", true)
			getApiData("str")
		}
		if(type == "meta" || !type) {
			LogAction("${str}Meta Data (forcedPoll)", "info", true)
			getApiData("meta")
		}
		updTimestampMap("lastWebUpdDt")
		schedNextWorkQ()
	} else {
		LogAction("Too Soon for Gathering New Data | Elapsed Wait (${lastFrcdPoll}sec.) seconds | Minimum Wait (${refreshWait()}sec.)", "debug", true)
		state.needStrPoll = true
		state.needDevPoll = true
	}
	state.forceChildUpd = true
	updateChildData()
}

void postCmd() {
	//LogTrace("postCmd()")
	poll()
}

void remDiagAppAvail(Boolean available) {
	state.remDiagAppAvailable = (available == true)
}

void createSavedNest() {
	String str = "createSavedNest"
	LogTrace("${str}")
	if((Boolean)state.isInstalled) {
		Map bbb = [:]
		Boolean bad = false
		if((String)settings.structures && (String)state.structures) {
			Map structs = getNestStructures()
			String newStrucName = structs && structs."${(String)state.structures}" ? (String)structs[(String)state.structures] : sNULL
			if(newStrucName) {
				bbb.a_structures_setting = (String)settings.structures
				bbb.a_structures_as = (String)state.structures
				bbb.a_structure_name_as = (String)state.structureName

				Map dData = deviceDataFLD
				Map t0

				t0 = dData?.thermostats?.findAll { (String)it.key in settings.thermostats }
				LogAction("${str} | Thermostats(${t0?.size()}): ${settings.thermostats}", "info", true)
				Map t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1?."${devItem.key.toString()}" = devItem?.value?.name
					}
				}
				Integer t3 = settings.thermostats?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Thermostat Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb?.b_thermostats_as = (List)settings.thermostats && dData && (Map)state.thermostats ? t1 : [:]
				bbb?.b_thermostats_setting = (List)settings.thermostats ?: []

				dData = deviceDataFLD
				t0 = [:]
				t0 = dData?.smoke_co_alarms?.findAll { (String)it.key in (List)settings.protects }
				LogAction("${str} | Protects(${t0?.size()}): ${(List)settings.protects}", "info", true)
				t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1."${devItem.key}" = devItem?.value?.name
					}
				}
				t3 = ((List)settings.protects)?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Protect Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb.c_protects_as = (List)settings.protects && dData && state.protects ? t1 : [:]
				bbb.c_protects_settings = (List)settings.protects ?: []

				dData = deviceDataFLD
				t0 = [:]
				t0 = dData?.cameras?.findAll { it?.key?.toString() in (List)settings.cameras }
				LogAction("${str} | Cameras(${t0?.size()}): ${settings.cameras}", "info", true)
				t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1."${devItem?.key}" = devItem?.value?.name
					}
				}
				t3 = ((List)settings.cameras)?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Camera Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb.d_cameras_as = (List)settings.cameras && dData && state.cameras ? t1 : [:]
				bbb.d_cameras_setting = (List)settings.cameras ?: []
			} else { LogAction("${str}: No Structures Found!!!", "warn", true) }

			def t0 = state.savedNestSettings ?: null
			String t1 = t0 ? new groovy.json.JsonOutput().toJson(t0) : sNULL
			String t2 = bbb != [:] ? new groovy.json.JsonOutput().toJson(bbb) : sNULL
			if(bad) {
				state.savedNestSettingsprev = state.savedNestSettings
				state.savedNestSettingslastbuild = bbb
				state.remove("savedNestSettings")
			}
			if(!bad && t2 && (!t0 || t1 != t2)) {
				state.savedNestSettings = bbb
				state.remove("savedNestSettingsprev")
				state.remove("savedNestSettingslastbuild")
				//return //true
			}
		} else { LogAction("${str}: No Structure Settings", "warn", true) }
	} else { LogAction("${str}: NOT Installed!!!", "warn", true) }
	//return //false
}

void mySettingUpdate(String name, value, String type=sNULL) {
	if(getDevOpt()) {
		LogAction("Setting $name set to type:($type) $value", "warn", true)
		if(!(Boolean)state.ReallyChanged) { return }
	}
	if((Boolean)state.ReallyChanged) {
		settingUpdate(name, value, type)
	}
}

void checkRemapping() {
	String str = "checkRemapping"
	LogTrace(str)
	String astr = ""
	state.ReallyChanged = false
	Boolean myRC = (Boolean)state.ReallyChanged
	if((Boolean)state.isInstalled && (String)settings.structures) {
		Boolean aastr = getApiData("str")
		Boolean aadev = getApiData("dev")
		//def aameta = getApiData("meta")
		Map sData = (Map)state.structData
		Map dData = deviceDataFLD
		//def mData = state.metaData
		def savedNest = state.savedNestSettings
		if(sData && dData /* && mData */ && savedNest) {
			Map structs = getNestStructures()
			if(structs && !getDevOpt() ) {
				LogAction("${str}: nothing to do ${structs}", "info", true)
				//return
			} else {
				astr += "${str}: found the mess..cleaning up ${structs}"
				state.pollBlocked = true
				state.pollBlockedReason = "Remapping"

				String newStructures_settings = ""
				List newThermostats_settings = []
				Map newvThermostats = [:]
				List newProtects_settings = []
				List newCameras_settings = []
				String oldPresId = getNestPresId()

				sData?.each { strucId ->
					def t0 = strucId.key
					def t1 = strucId.value
					Logger("checkRempapping: t1.name: ${t1?.name?.toString()}   a_structure_name_as: ${savedNest?.a_structure_name_as?.toString()}", "info")
					if(t1?.name && t1?.name?.toString() == savedNest?.a_structure_name_as?.toString()) {
						newStructures_settings = [t1?.structure_id]?.join('.') as String
					}
				}
				Logger("checkRempapping: newStructures_settings: ${newStructures_settings.toString()}", "info")
				if((String)settings.structures && newStructures_settings) {
					if((String)settings.structures != newStructures_settings) {
						state.ReallyChanged = true
						myRC = (Boolean)state.ReallyChanged
						astr += ", STRUCTURE CHANGED"
					} else {
						astr += ", NOTHING REALLY CHANGED (DEVELOPER MODE)"
					}
				} else { astr += ", no new structure found" }
				LogAction(astr, "warn", true)
				astr = ""
				if(myRC || (newStructures_setting && getDevOpt())) {
					mySettingUpdate("structures", newStructures_settings, "enum")
					if(myRC) { state.structures = newStructures_settings }
					String newStrucName = newStructures_settings ? (String)((Map)state.structData)[newStructures_settings]?.name : sNULL
					astr = "${str}: newStructures ${newStructures_settings} | name: ${newStrucName} | to settings & as structures: ${(String)settings.structures}"

	//				astr += ",\n as.thermostats: ${state.thermostats}  |  saveNest: ${savedNest?.b_thermostats_as}\n"
					LogAction(astr, "info", true)
					savedNest?.b_thermostats_as.each { dni ->
						String t0 = dni?.key
						def dev = getChildDevice(t0)
						if(dev) {
	//						LogAction("${str}: myRC : ${myRC}  found dev oldId: ${t0}", "info", true)
							Boolean gotIt = false
							dData?.thermostats?.each { devItem ->
								String t21 = devItem.key
								def t22 = devItem.value
								String newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									def t6 = [t22?.device_id].join('.')
									def t7 = [ ("${t6}".toString()) : dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestTstatDni(ba)
									}
									newThermostats_settings << newDevId
									gotIt = true

									String rstr = "found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} |"
/*
									if(settings."${t0}_safety_temp_min") {
										mySettingUpdate("${newDevId}_safety_temp_min", settings."${t0}_safety_temp_min", "decimal")
										mySettingUpdate("${t0}_safety_temp_min", "")
										rstr += ", safety min"
									}
									if(settings."${t0}_safety_temp_max") {
										mySettingUpdate("${newDevId}_safety_temp_max", settings."${t0}_safety_temp_max", "decimal")
										mySettingUpdate("${t0}_safety_temp_max", "")
										rstr += ", safety max"
									}
									if(settings."${t0}_comfort_dewpoint_max") {
										mySettingUpdate("${newDevId}_comfort_dewpoint_max", settings."${t0}_comfort_dewpoint_max", "decimal")
										mySettingUpdate("${t0}_comfort_dewpoint_max", "")
										rstr += ", comfort dew"
									}
									if(settings."${t0}_comfort_humidity_max") {
										mySettingUpdate("${newDevId}_comfort_humidity_max", settings."${t0}_comfort_humidity_max", "number")
										mySettingUpdate("${t0}_comfort_humidity_max", "")
										rstr += ", comfort hum"
									}
*/
									if(settings."tstat_${t0}_lbl") {
										if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
											mySettingUpdate("tstat_${newDevId}_lbl", settings."tstat_${t0}_lbl", "text")
										}
										mySettingUpdate("tstat_${t0}_lbl", "")
										rstr += ", custom Label"
									}
									if(state.vThermostats && state."vThermostatv${t0}") {
										String physDevId = (String)state."vThermostatMirrorIdv${t0}"
										def t1 = state.vThermostats
										String t5 = 'v'+newDevId

										if(t0 && t0 == physDevId && t1?."v${physDevId}") {
											def vdev = getChildDevice("v${t0}")
											if(vdev) {
												rstr += ", there are virtual devices that match"

												if(settings."vtstat_v${t0}_lbl") {
													if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
														mySettingUpdate("vtstat_${t5}_lbl", settings."tstat_v${t0}_lbl", "text")
													}
													mySettingUpdate("vtstat_v${t0}_lbl", "")
													rstr += ", custom vstat Label"
												}

												newvThermostats."${t5}" = t1."v${t0}"
												if(myRC) {
													state."vThermostat${t5}" = state."vThermostatv${t0}"
													state."vThermostatMirrorId${t5}" = newDevId
													state."vThermostatChildAppId${t5}" = state."vThermostatChildAppIdv${t0}"
												}

												def automationChildApp = getChildApps().find{ it.id == state."vThermostatChildAppIdv${t0}" }
												if(automationChildApp != null) {
													if(myRC) { automationChildApp.setRemoteSenTstat(newDevId) }
													rstr += ", fixed state.remSenTstat"
												} else { rstr += ", DID NOT FIND AUTOMATION APP" }

												// fix locks
												def t3 = ""
												if(state."remSenLock${t0}") {
													rstr += ", fixed locks"
													if(myRC) {
														state."remSenLock${newDevId}" = state."remSenLock${t0}"
														t3 = "remSenLock${t0}";		state.remove(t3.toString())
													}
												} else { rstr += ", DID NOT FIND LOCK" }
												// find the virtual device and reset its dni
												rstr += ", reset vDNI"
												if(myRC) {
													vdev.deviceNetworkId = t5

													t3 = "vThermostatv${t0}";		state.remove(t3.toString())
													t3 = "vThermostatMirrorIdv${t0}";	state.remove(t3.toString())
													t3 = "vThermostatChildAppIdv${t0}";	state.remove(t3.toString())
												}

											} else { rstr += ", DID NOT FIND VIRTUAL DEVICE" }
											def t11 = "oldvstatDatav${t0}"
											state.remove(t11.toString())
										} else { rstr += ", vstat formality check failed" }
									} else { rstr += ", no vstat" }

									if(myRC) { dev.deviceNetworkId = newDevId }

									if(rstr != "") { LogAction("${str}: resultStr: ${rstr}", "info", true) }
								}
							}
							if(!gotIt) { LogAction("${str}: NOT matched dev oldId: ${t0}", "warn", true) }
						} else { LogAction("${str}: NOT found dev oldId: ${t0}", "error", true) }
						def t10 = "oldTstatData${t0}"
						state.remove(t10.toString())
					}
					astr = ""
					if((List)settings.thermostats) {
						Integer t0 = settings.thermostats?.size()
						Integer t1 = savedNest?.b_thermostats_as?.size()
						Integer t2 = newThermostats_settings.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("thermostats", newThermostats_settings, "enum")
							astr += "${str}: myRC: ${myRC}  newThermostats_settings: ${newThermostats_settings} settings.thermostats: ${settings.thermostats}"

							//LogAction("as.thermostats: ${state.thermostats}", "warn", true)
							state.thermostats = null

							def t4 = newvThermostats ? newvThermostats?.size() : 0
							def t5 = state.vThermostats ? state.vThermostats.size() : 0
							if(t4 || t5) {
								if(t4 == t5) {
									astr += ", AS vThermostats ${newvThermostats}"
									if(myRC) { state.vThermostats = newvThermostats }
								} else { LogAction("vthermostat sizes don't match ${t4} ${t5}", "warn", true) }
							}
							LogAction(astr, "info", true)
						} else { LogAction("thermostat sizes don't match ${t0} ${t1} ${t2}", "warn", true) }
					}

					astr = ""
					savedNest?.c_protects_as.each { dni ->
						String t0 = (String)dni.key
						def dev = getChildDevice(t0)
						if(dev) {
							Boolean gotIt = false
							dData?.smoke_co_alarms?.each { devItem ->
								astr = ""
								String t21 = devItem.key
								def t22 = devItem.value
								String newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									//def newDevId = [t22?.device_id].join('.')
									String t6 = [t22?.device_id].join('.')
									def t7 = [ ("${t6}".toString()):dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestProtDni(ba)
									}
									newProtects_settings << newDevId
									gotIt = true
									astr += "${str}: myRC: ${myRC}  found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} "
									LogAction(astr, "info", true)

									if(settings."prot_${t0}_lbl") {
										if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
											mySettingUpdate("prot_${newDevId}_lbl", settings."prot_${t0}_lbl", "text")
										}
										mySettingUpdate("prot_${t0}_lbl", "")
									}

									if(myRC) { dev.deviceNetworkId = newDevId }
								}
							}
							if(!gotIt) { LogAction("${str}: NOT matched dev oldId: ${t0}", "warn", true) }
						} else { LogAction("${str}: NOT found dev oldId: ${t0}", "error", true) }
						def t10 = "oldProtData${t0}"
						state.remove(t10.toString())
					}
					astr = ""
					if((List)settings.protects) {
						Integer t0 = settings.protects?.size()
						Integer t1 = savedNest?.c_protects_as?.size()
						Integer t2 = newProtects_settings.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("protects", newProtects_settings, "enum")
							astr += "newProtects: ${newProtects_settings} settings.protects: ${settings.protects} "
							//LogAction("as.protects: ${state.protects}", "warn", true)
							state.protects = null
						} else { LogAction("protect sizes don't match ${t0} ${t1} ${t2}", "warn", true) }
						LogAction(astr, "info", true)
					}

					astr = ""
					savedNest?.d_cameras_as.each { dni ->
						String t0 = (String)dni.key
						def dev = getChildDevice(t0)
						if(dev) {
							Boolean gotIt = false
							dData?.cameras?.each { devItem ->
								astr = ""
								String t21 = devItem.key
								def t22 = devItem.value
								String newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									//def newDevId = [t22?.device_id].join('.')
									String t6 = [t22?.device_id].join('.')
									def t7 = [ ("${t6}".toString()):dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestCamDni(ba)
									}
									newCameras_settings << newDevId
									gotIt = true
									astr += "${str}: myRC: ${myRC}  found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} "
									LogAction(astr, "info", true)

									if(settings."cam_${t0}_lbl") {
										if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
											mySettingUpdate("cam_${newDevId}_lbl", settings."cam_${t0}_lbl", "text")
										}
										mySettingUpdate("cam_${t0}_lbl", "")
									}

									if(myRC) { dev.deviceNetworkId = newDevId }
								}
							}
							if(!gotIt) { LogAction("${str}: NOT matched dev oldId: ${t0}", "warn", true) }
						} else { LogAction("${str}: NOT found dev oldId: ${t0}", "error", true) }
						String t10 = "oldCamData${t0}"
						state.remove(t10)
					}
					astr = ""
					if((List)settings.cameras) {
						Integer t0 = settings.cameras?.size()
						Integer t1 = savedNest?.d_cameras_as?.size()
						Integer t2 = newCameras_settings.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("cameras", newCameras_settings, "enum")
							astr += "${str}: newCameras_settings: ${newCameras_settings} settings.cameras: ${settings.cameras}"
							//LogAction("as.cameras: ${state.cameras}", "warn", true)
							state.cameras = null
						} else { LogAction("camera sizes don't match ${t0} ${t1} ${t2}", "warn", true) }
						LogAction(astr, "info", true)
					}

/*
	The Settings changes made above "do not take effect until a state re-load happens - so you cannot call these here, need to wait a runIn
					if(myRC) {
						fixDevAS()
					}
*/
					astr = "oldPresId $oldPresId "
					// fix presence
					if((Boolean)settings.presDevice) {
						if(oldPresId) {
							def dev = getChildDevice(oldPresId)
							String newId = getNestPresId()
							def ndev = getChildDevice(newId)
							astr += "| DEV ${dev?.deviceNetworkId} | NEWID $newId |  NDEV: ${ndev?.deviceNetworkId} "
							String t10 = "oldPresData${dev?.deviceNetworkId}".toString()
							state.remove(t10)
							if(dev && newId && ndev) { astr += " all good presence" }
							else if(!dev) { astr += "where is the pres device?" }
							else if(dev && newId && !ndev) {
								astr += "will fix presence "
								if(myRC) { dev.deviceNetworkId = newId }
							} else { LogAction("${dev?.label} $newId ${ndev?.label}", "error", true) }
						} else { LogAction("no oldPresId", "error", true) }
						LogAction(astr, "info", true)
					}
/*
					// fix weather
					astr += "oldWeatId $oldWeatId "
					if(settings.weatherDevice) {
						if(oldWeatId) {
							def dev = getChildDevice(oldWeatId)
							def newId = getNestWeatherId()
							def ndev = getChildDevice(newId)
							astr += "| DEV ${dev?.deviceNetworkId} | NEWID $newId |  NDEV: ${ndev?.deviceNetworkId} "
							def t10 = "oldWeatherData${dev?.deviceNetworkId}"
							state.remove(t10.toString())
							if(dev && newId && ndev) { astr += " all good weather " }
							else if(!dev) { LogAction("where is the weather device?", "warn", true) }
							else if(dev && newId && !ndev) {
								astr += "will fix weather"
								if(myRC) { dev.deviceNetworkId = newId }
							} else { LogAction("${dev?.label} $newId ${ndev?.label}", "error", true) }
						} else { LogAction("no oldWeatId", "error", true) }
					}
					LogAction(astr, "info", true)
*/

				} else { LogAction("no changes or no data a:${(String)settings.structures} b: ${newStructures_settings}", "info", true) }

				state.pollBlocked = false
				state.pollBlockedReason = ""
				//return
			}
		} else { LogAction("don't have our data", "warn", true) }
	} else { LogAction("not installed, no structure", "warn", true) }
}

void fixDevAS() {
	LogTrace("fixDevAS")
	if((List)settings.thermostats && !(Map)state.thermostats) { state.thermostats = (List)settings.thermostats ? statState((List)settings.thermostats) : null }
	if((List)settings.protects && !(Map)state.protects) { state.protects = (List)settings.protects ? coState((List)settings.protects) : null }
	if((List)settings.cameras && !(Map)state.cameras) { state.cameras = (List)settings.cameras ? camState((List)settings.cameras) : null }
	state.presDevice = (Boolean)settings.presDevice ?: null
	//state.weatherDevice = settings.weatherDevice ?: null
}

private Boolean getApiData(String type = sNULL) {
	//LogTrace("getApiData($type)")
	Boolean result = false
	if(!type || getNestAuthToken()==sNULL) { return result }

	switch(type) {
		case "str":
		case "dev":
		case "meta":
			break
		default:
			return result
	}
	String tPath = (type == "str") ? "/structures" : ((type == "dev") ? "/devices" : "/")
	Map params = [
			uri: getNestApiUrl(),
			path: "$tPath",
			contentType: "application/json",
			headers: ["Authorization": "Bearer ${getNestAuthToken()}"]
	]
	try {
		httpGet(params) { resp ->
			if(resp?.status == 200) {
				updTimestampMap("lastHeardFromNestDt", getDtNow())
				apiIssueEvent(false)
				//state.apiRateLimited = false
				//state.apiCmdFailData = null
				if(type == "str") {
					Map t0 = resp?.data
					//LogTrace("API Structure Resp.Data: ${t0}")
					if((Map)state.structData == null) { state.structData = t0 }
					Boolean chg = didChange((Map)state.structData, t0, "str", "poll")
					if(chg) {
						result = true
						String newStrucName = ((Map)state.structData)?.size() && (String)state.structures ? (String)((Map)state.structData)[(String)state.structures]?.name : sNULL
						state.structureName = newStrucName ?: (String)state.structureName
					}
				}
				else if(type == "dev") {
					def t0 = resp?.data
					//LogTrace("API Device Resp.Data: ${t0}")
					Boolean chg = didChange(deviceDataFLD, t0, "dev", "poll")
					if(chg) { result = true }
				}
				else if(type == "meta") {
					//LogTrace("API Metadata Resp.Data: ${resp?.data}")
					def nresp = resp?.data?.metadata
					Boolean chg = didChange(state.metaData, nresp, "meta", "poll")
					if(chg) { result = true }
				}
			} else {
				LogAction("getApiData - ${type} Received: Resp (${resp?.status})", "error", true)
				apiRespHandler(resp?.status, resp?.data, 'getApiData('+type+')', type+' Poll')
				apiIssueEvent(true)
				state.forceChildUpd = true
			}
		}
	} catch (ex) {
		//state.apiRateLimited = false
		state.forceChildUpd = true
		if(ex instanceof groovyx.net.http.HttpResponseException && ex?.response) {
			apiRespHandler(ex?.response?.status, ex?.response?.data, 'getApiData(ex catch)', type+' Poll')
		} else {
			if(type == "str") { state.needStrPoll = true }
			else if(type == "dev") { state.needDevPoll = true }
			else if(type == "meta") { state.needMetaPoll = true }
		}
		apiIssueEvent(true)
		log.error "getApiData (type: $type) Exception: ${ex?.message}"
	}
	return result
}

def streamDeviceInstalled(val) { state.streamDevice = val }

//void eventStreamActive(Boolean val) { state.eventStreamActive = val }

@Field static Map deviceDataFLD

void receiveEventData(evtData) {
	Map status = [:]
//	try {
		// LogAction("evtData: $evtData", "trace", true)
		Boolean devChgd = false
		Boolean gotSomething = false
		if(evtData && evtData.data && restEnabled()) {
			if(!(Boolean)state.restStreamingOn) {
				state.restStreamingOn = true
			//	apiIssueEvent(false)
			}
			if((Map)evtData.data.devices) {
				//LogTrace("API Device Resp.Data: ${evtData?.data?.devices}")
				gotSomething = true
				Boolean chg = didChange(deviceDataFLD, (Map)evtData.data.devices, "dev", "stream")
				if(chg) {
					devChgd = true
				} //else { LogTrace("got deviceData") }
			}
			if((Map)evtData.data.structures) {
				//LogTrace("API Structure Resp.Data: ${evtData?.data?.structures}")
				gotSomething = true
				Boolean chg = didChange((Map)state.structData, (Map)evtData.data.structures, "str", "stream")
				if(chg) {
					String newStrucName = state.structData && (String)state.structures ? (String)state.structData[(String)state.structures]?.name : sNULL
					state.structureName = newStrucName ?: (String)state.structureName
				} //else { LogTrace("got structData") }
			}
			if(evtData.data.metadata) {
				//LogTrace("API Metadata Resp.Data: ${evtData?.data?.metadata}")
				gotSomething = true
				Boolean chg = didChange((Map)state.metaData, (Map)evtData.data.metadata, "meta", "stream")
				//if(!chg) { LogTrace("got metaData") }
			}
		} else {
			LogAction("Did not receive any data in stream response - likely stream shutdown", "warn", true)
			updTimestampMap("lastHeardFromNestDt")
			//apiIssueEvent(true)
//			if((Boolean)state.restStreamingOn) {
//				restStreamHandler(true, "receiveEventData(no data)")
//			}
//			state.restStreamingOn = false
			runIn(6, "pollFollow", [overwrite: true])
		}
		if(gotSomething) {
			updTimestampMap("lastHeardFromNestDt", getDtNow())
			//apiIssueEvent(false)
			//state.apiRateLimited = false
			//state.apiCmdFailData = null
		}
		if((Boolean)state.forceChildUpd || (Boolean)state.needChildUpd || devChgd) {
			schedFinishPoll(devChgd)
		}
		status = ["data":"status received...ok", "code":200]
//	} catch (ex) {
//		log.error "receiveEventData Exception: ${ex?.message}"
//		status = ["data":"${ex?.message}", "code":500]
//	}
}

Boolean didChange(Map old, Map newer, String type, String src) {
	//LogTrace("didChange: type: $type  src: $src")
	Boolean result = false
	String srcStr = src.toUpperCase()
	if(newer != null) {
		if(type == "str") {
			updTimestampMap("lastStrDataUpd", getDtNow())
			state.needStrPoll = false
			newer.each {   // reduce stored state size
				if(it?.value) {
					def myId = it?.value?.structure_id
					if(myId) {
						newer[myId].wheres = [:]
					}
				}
			}
		}
		if(type == "dev") {
			updTimestampMap("lastDevDataUpd", getDtNow())
			state.needDevPoll = false
			newer.each { t ->		// This reduces stored state size
				String dtyp = t.key
				t.value.each {
					if(it?.value) {
						def myId = it?.value?.device_id
						if(myId) {
							newer."${dtyp}"[myId].where_id = ""
							if(newer."${dtyp}"[myId]?.app_url) {
								newer."${dtyp}"[myId].app_url = ""
							}
							if(newer."${dtyp}"[myId]?.last_event?.app_url) {
								newer."${dtyp}"[myId].last_event.app_url = ""
							}
							if(newer."${dtyp}"[myId]?.last_event?.image_url) {
								newer."${dtyp}"[myId].last_event.image_url = ""
							}
						}
					}
				}
			}
		}
		if(type == "meta") {
			updTimestampMap("lastMetaDataUpd", getDtNow())
			state.needMetaPoll = false
		}
		if(old != newer) {
			if(type == "str") {
				Map tt0 = (Integer)((Map)state.structData)?.size() ? state.structData : null
				// Null safe does not work on array references that miss
                String myStruc=(String)state.structures
				Map t0 = tt0 && myStruc && tt0."${myStruc}" ?  tt0[myStruc] : null
				Map t1 = newer && myStruc && newer."${myStruc}" ? newer[myStruc] : null

				if(t1 && t0 != t1) {
					result = true
					state.forceChildUpd = true
					if((Boolean)settings.showDataChgdLogs == true && (Boolean)state.enRemDiagLogging != true) {
						List chgs = getChanges(t0, t1, "/structures", "structure")
						if(chgs) { LogAction("STRUCTURE Data Changed ($srcStr): ${chgs}", "info", false) }
					} else {
						LogAction("Nest Structure Data HAS Changed ($srcStr)", "info", false)
					}
				}
				state.structData = newer
			}
			else if(type == "dev") {
				Boolean devChg = false
				def tstats = state.thermostats.collect { dni ->
					String t1 = (String)dni.key
					if(t1 && old && old.thermostats && newer.thermostats && old.thermostats[t1] && newer.thermostats[t1] && old.thermostats[t1] == newer.thermostats[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old.thermostats && newer.thermostats && old.thermostats[t1] && newer.thermostats[t1]) {
							if((Boolean)settings.showDataChgdLogs == true && (Boolean)state.enRemDiagLogging != true) {
								List chgs = getChanges(old.thermostats[t1], newer.thermostats[t1], "/devices/thermostats/${t1}".toString(), "thermostat")
								if(chgs) { LogAction("THERMOSTAT Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}

				def nProtects = state.protects.collect { dni ->
					String t1 = (String)dni.key
					if(t1 && old && old.smoke_co_alarms && newer.smoke_co_alarms && old.smoke_co_alarms[t1] && newer.smoke_co_alarms[t1] && old.smoke_co_alarms[t1] == newer.smoke_co_alarms[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old.smoke_co_alarms && newer.smoke_co_alarms && old.smoke_co_alarms[t1] && newer.smoke_co_alarms[t1]) {
							if((Boolean)settings.showDataChgdLogs == true && (Boolean)state.enRemDiagLogging != true) {
								List chgs = getChanges(old.smoke_co_alarms[t1], newer.smoke_co_alarms[t1], "/devices/smoke_co_alarms/${t1}".toString(), "protect")
								if(chgs) { LogAction("PROTECT Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}

				def nCameras = state.cameras.collect { dni ->
					String t1 = (String)dni.key
					if(t1 && old && old.cameras && newer.cameras && old.cameras[t1] && newer.cameras[t1] && old.cameras[t1] == newer.cameras[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old.cameras && newer.cameras && old.cameras[t1] && newer.cameras[t1]) {
							if((Boolean)settings.showDataChgdLogs == true && (Boolean)state.enRemDiagLogging != true) {
								List chgs = getChanges(old.cameras[t1], newer.cameras[t1], "/devices/cameras/${t1}".toString(), "camera")
								if(chgs) { LogAction("CAMERA Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}
				if(devChg && ((Boolean)settings.showDataChgdLogs != true)) { LogAction("Nest Device Data HAS Changed ($srcStr)", "info", false) }
				deviceDataFLD = null
				deviceDataFLD = newer
			}
			else if(type == "meta") {
				result = true
				state.needChildUpd = true
				state.metaData = newer
/*
				if((Boolean)settings.showDataChgdLogs != true) {
					LogAction("Nest MetaData HAS Changed ($srcStr)", "info", false)
				} else {
					List chgs = getChanges(old, newer, "/metadata", "metadata")
					if(chgs) {
						LogAction("METADATA Changed ($srcStr): ${chgs}", "info", false)
					}
				}
*/
			}
		}
	}
	//LogAction("didChange: type: $type  src: $src result: $result", "info", true)
	return result
}

List getChanges(mapA, mapB, String headstr, String objType=sNULL) {
	def t0 = mapA
	def t1 = mapB
	def left = t0
	def right = t1
	List itemsChgd = []
	if(left instanceof Map) {
		String[] leftKeys = left.keySet()
		//String[] rightKeys = right.keySet()
		leftKeys.each {
			if( left[it] instanceof Map ) {
				String tstr=headstr+'/'+it
				List chgs = getChanges( left[it], right[it], tstr, objType )
				if(chgs && objType!=sNULL) {
					itemsChgd += chgs
				}
			} else {
				if(left[it].toString() != right[it].toString()) {
					if(objType!=sNULL) {
//						LogTrace("getChanges ${headstr} IT: ${it}  LEFT: ${left[it]}   RIGHT:${right[it]}")
						itemsChgd.push(it.toString())
					}
				}
			}
		}
		if((Integer)itemsChgd.size()) { return itemsChgd }
	}
	return null
}

private String generateMD5_A(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

void updateChildData(Boolean force = false) {
	LogTrace("updateChildData(force: $force) | forceChildUpd: ${(Boolean)state.forceChildUpd} | needChildUpd: ${(Boolean)state.needChildUpd} | pollBlocked: ${(Boolean)state.pollBlocked}".toString())
	if((Boolean)state.pollBlocked) { return }
	Boolean nforce = (Boolean)state.forceChildUpd
	//state.forceChildUpd = true
	try {
		updTimestampMap("lastChildUpdDt", getDtNow())
		if(force || nforce) {
			updTimestampMap("lastChildForceUpdDt", getDtNow())
		}
		String nestTz = getNestTimeZone()?.toString()
		String api = apiIssueDesc()
		String locPresence = getLocationPresence()
		String locSecurityState = getSecurityState()
		String locEtaBegin = getEtaBegin()
		String locPeakStart = getPeakStart()
		String locPeakEnd = getPeakEnd()
//		Boolean restStreamingEn = restEnabled() != false
		if((Boolean)settings.devNameOverride == null /* || (Boolean)state.useAltNames == null || (Boolean)state.custLabelUsed == null */ ) { // Install / Upgrade force to on
			state.devNameOverride = true
			settingUpdate("devNameOverride", "true", "bool")
			state.useAltNames = true
			settingUpdate("useAltNames", "true", "bool")
			state.custLabelUsed = false
			settingUpdate("useCustDevNames", "false", "bool")
		} else {
			state.devNameOverride = (Boolean)settings.devNameOverride ? true : false
			if((Boolean)state.useAltNames == null || (Boolean)state.custLabelUsed == null) {
				if((Boolean)state.devNameOverride) {
					state.useAltNames = (Boolean)settings.useAltNames ? true : false
					state.custLabelUsed = (Boolean)settings.useCustDevNames ? true : false
				} else {
					state.useAltNames = false
					state.custLabelUsed = false
				}
			}
		}

		Boolean overRideNames = ((Boolean)state.devNameOverride) ? true : false
		def devices = getChildDevices()
		devices?.each {
			if((Boolean)state.pollBlocked) { return true }
			String devId = it?.deviceNetworkId
			if(devId && (List)settings.thermostats && deviceDataFLD?.thermostats && deviceDataFLD?.thermostats[devId]) {
				Map tData = [data: deviceDataFLD.thermostats[devId], tz: nestTz, apiIssues: api, pres: locPresence, childWaitVal: getChildWaitVal().toInteger(), etaBegin: locEtaBegin]

				String oldTstatData = (String)state."oldTstatData${devId}"
				String tDataChecksum = generateMD5_A(tData.toString())
				state."oldTstatData${devId}" = tDataChecksum
				tDataChecksum = (String)state."oldTstatData${devId}"

				if(tData && (force || nforce || oldTstatData != tDataChecksum)) {
					physDevLblHandler("thermostat", devId, (String)it?.label, "thermostats", (String)tData.data?.name, "tstat", overRideNames)
					it.generateEvent(tData)
				} else { /* LogTrace("tstat ${devId} did not change") */ }
				return true
			}
			else if(devId && (List)settings.protects && deviceDataFLD?.smoke_co_alarms && deviceDataFLD?.smoke_co_alarms[devId]) {
				Map pData = [data: deviceDataFLD.smoke_co_alarms[devId], showProtActEvts: (!showProtActEvts ? false : true), tz: nestTz, apiIssues: api ]
				String oldProtData = (String)state."oldProtData${devId}"
				String pDataChecksum = generateMD5_A(pData.toString())
				state."oldProtData${devId}" = pDataChecksum
				pDataChecksum = (String)state."oldProtData${devId}"

				if(pData && (force || nforce || oldProtData != pDataChecksum)) {
					physDevLblHandler("protect", devId, (String)it?.label, "protects", (String)pData.data?.name, "prot", overRideNames)
					it.generateEvent(pData)
				} else { /* LogTrace("prot ${devId} did not change") */ }
				return true
			}
			else if(devId && (List)settings.cameras && deviceDataFLD?.cameras && deviceDataFLD?.cameras[devId]) {
				Map camData = [data: deviceDataFLD.cameras[devId], tz: nestTz, apiIssues: api, motionSndChgWaitVal: motionSndChgWaitVal, secState: locSecurityState ]
				String oldCamData = (String)state."oldCamData${devId}"
				String cDataChecksum = generateMD5_A(camData.toString())
				state."oldCamData${devId}" = cDataChecksum
				cDataChecksum = (String)state."oldCamData${devId}"

				if(camData && (force || nforce || oldCamData != cDataChecksum)) {
					physDevLblHandler("camera", devId, (String)it?.label, "cameras", (String)camData.data?.name, "cam", overRideNames)
					it.generateEvent(camData)
				} else { /* LogTrace("cam ${devId} did not change") */ }
				return true
			}
			else if(devId && (Boolean)settings.presDevice && devId == getNestPresId()) {
				Map pData = [tz:nestTz, pres: locPresence, apiIssues: api, etaBegin: locEtaBegin, secState: locSecurityState, peakStart: locPeakStart, peakEnd: locPeakEnd ]
				String oldPresData = (String)state."oldPresData${devId}"
				String pDataChecksum = generateMD5_A(pData.toString())
				state."oldPresData${devId}" = pDataChecksum
				pDataChecksum = (String)state."oldPresData${devId}"

				pData = [tz:nestTz, pres: locPresence, apiIssues: api, lastStrDataUpd: getTimestampVal("lastStrDataUpd"), etaBegin: locEtaBegin, secState: locSecurityState, peakStart: locPeakStart, peakEnd: locPeakEnd ]

				if(pData && (force || nforce || oldPresData != pDataChecksum)) {
					virtDevLblHandler(devId, (String)it?.label, "pres", "pres", overRideNames)
					it.generateEvent(pData)
				} else { /* LogTrace("pres ${devId} did not change") */ }
				return true
			}


			else if(devId && state.vThermostats && state."vThermostat${devId}") {
				String physdevId = (String)state."vThermostatMirrorId${devId}"
				if(physdevId && settings.thermostats && deviceDataFLD?.thermostats && deviceDataFLD?.thermostats[physdevId]) {
					Map tmp_data = deviceDataFLD.thermostats[physdevId]
					Map data = tmp_data
					def automationChildApp = getChildApps().find{ it.id == state."vThermostatChildAppId${devId}" }
					if(automationChildApp != null && !(Boolean)automationChildApp.getIsAutomationDisabled()) {
						//data = new JsonSlurper().parseText(JsonOutput.toJson(tmp_data))  // This is a deep clone as object is same reference
						data = [:] + tmp_data  // This is a deep clone as object is same reference
						def tempC
						def tempF
						if(getTemperatureScale() == "C") {
							tempC = automationChildApp.getRemoteSenTemp()
							tempF = (tempC * (9 / 5) + 32.0)
						} else {
							tempF = automationChildApp.getRemoteSenTemp()
							tempC = (tempF - 32.0) * (5 / 9) as Double
						}
						data.ambient_temperature_c = tempC
						data.ambient_temperature_f = tempF

						def ctempC
						def ctempF
						if(getTemperatureScale() == "C") {
							ctempC = automationChildApp.getRemSenCoolSetTemp()
							ctempF = ctempC != null ? (ctempC * (9 / 5) + 32.0) as Integer : null
						} else {
							ctempF = automationChildApp.getRemSenCoolSetTemp()
							ctempC = ctempF != null ? (ctempF - 32.0) * (5 / 9) as Double : null
						}

						def htempC
						def htempF
						if(getTemperatureScale() == "C") {
							htempC = automationChildApp.getRemSenHeatSetTemp()
							htempF = htempC != null ? (htempC * (9 / 5) + 32.0) as Integer : null
						} else {
							htempF = automationChildApp.getRemSenHeatSetTemp()
							htempC = htempF != null ? (htempF - 32.0) * (5 / 9) as Double : null
						}

						if((String)data?.hvac_mode == "heat-cool") {
							data.target_temperature_high_f = ctempF
							data.target_temperature_low_f = htempF
							data.target_temperature_high_c = ctempC
							data.target_temperature_low_c = htempC
						} else if((String)data?.hvac_mode == "cool") {
							data.target_temperature_f = ctempF
							data.target_temperature_c = ctempC
						} else if((String)data?.hvac_mode == "heat") {
							data.target_temperature_f = htempF
							data.target_temperature_c = htempC
						}
					}


					Map tData = [data: data, tz: nestTz, apiIssues: api, pres: locPresence, childWaitVal: getChildWaitVal().toInteger(), etaBegin: locEtaBegin, virt: true]

					String oldTstatData = (String)state."oldvstatData${devId}"
					String tDataChecksum = generateMD5_A(tData.toString())
					state."oldvstatData${devId}" = tDataChecksum
					tDataChecksum = (String)state."oldvstatData${devId}"

					if(tData && (force || nforce || oldTstatData != tDataChecksum)) {
						physDevLblHandler("vthermostat", devId, (String)it?.label, "vThermostats", (String)tData.data?.name, "vtstat", overRideNames)
						it.generateEvent(tData)
					} else { /* LogTrace("tstat ${devId} did not change") */ }
					return true
				}
			}

			else if(devId && devId == getNestPresId()) {
				return true
			}
			else if(devId && devId == getEventDeviceDni()) {
				return true
			}
			else {
				LogAction("updateChildData() | Unclaimed Device Found (or device with no data available from Nest): (${it?.displayName}) $devId", "warn", true)
				return true
			}
		}
	}
	catch (ex) {
		log.error "updateChildData Exception: ${ex}"
		updTimestampMap("lastChildUpdDt")
		return
	}
	if((Boolean)state.pollBlocked) { return }
	if((Boolean)state.forceChildUpd) state.forceChildUpd = false
	if((Boolean)state.needChildUpd)  state.needChildUpd = false
}

String tUnitStr() {
	return "\u00b0${getTemperatureScale()}".toString()
}

private void setDeviceLabel(String devId, String labelStr) {
	if(labelStr) {
		def dev = getChildDevice(devId)
		dev.label = labelStr
	}
}

private void physDevLblHandler(String devType, String devId, String devLbl, String devStateName, String apiName, String abrevStr, Boolean ovrRideNames) {
	Boolean nameIsDefault = false
	String deflbl
	String deflblval
	state."${devStateName}"?.each { t ->
		if(t.key == devId) {
			deflblval = (String)t.value
			deflbl = getDefaultLabel(devType, deflblval)
		}
	}
	String curlbl = devLbl
	if(deflbl && deflbl == curlbl) { nameIsDefault = true }
	String newlbl = "getNest${abrevStr.capitalize()}Label"(apiName, devId)
	//LogTrace("physDevLblHandler | deflbl: ${deflbl} | curlbl: ${curlbl} | newlbl: ${newlbl} | deflblval: ${deflblval} || devId: ${devId}")
	if(ovrRideNames || (nameIsDefault && curlbl != newlbl)) {		// label change from nest
		if(curlbl != newlbl) {
			LogAction('Changing Name of Device from '+curlbl+' to '+newlbl, "info", true)
			setDeviceLabel(devId, newlbl)
			curlbl = newlbl
		}
		def t0 = state."${devStateName}"
		t0[devId] = apiName
		state."${devStateName}" = t0
	}

	String tstr="${abrevStr}_${devId}_lbl".toString()
	if((Boolean)state.custLabelUsed && settings."${tstr}" != curlbl) {
		settingUpdate(tstr, curlbl)
	}
	if(!(Boolean)state.custLabelUsed && settings."${tstr}") { settingUpdate(tstr, "") }
	tstr="${abrevStr}_${deflblval}_lbl".toString()
	if(settings."${tstr}") { settingUpdate(tstr, "") } // clean up old stuff
}

private void virtDevLblHandler(devId, String devLbl, String devMethAbrev, String abrevStr, Boolean ovrRideNames) {
	String curlbl = devLbl
	String newlbl = "getNest${devMethAbrev.capitalize()}Label"()
	//LogTrace("virtDevLblHandler | curlbl: ${curlbl} | newlbl: ${newlbl} || devId: ${devId}")
	if(ovrRideNames && curlbl != newlbl) {
		LogAction("Changing Name of Device from ${curlbl} to ${newlbl}", "info", true)
		setDeviceLabel(devId, newlbl?.toString())
		curlbl = newlbl?.toString()
	}

	if((Boolean)state.custLabelUsed && settings."${abrevStr}Dev_lbl" != curlbl) {
		settingUpdate("${abrevStr}Dev_lbl", curlbl?.toString())
	}
	if(!(Boolean)state.custLabelUsed && settings."${abrevStr}Dev_lbl") { settingUpdate("${abrevStr}Dev_lbl", "") }

}

def apiIssues() {
	List t0 = (List)state.apiIssuesList ?: [false, false, false, false, false, false, false]
	state.apiIssuesList = t0
	def result = t0[5..-1].every { it == true }  // last 2
	String dt = getTimestampVal("apiIssueDt")
	if(result) {
		String str = dt ? "may still be occurring. Status will clear when last updates are good (Last Updates: ${t0}) | Issues began at ($dt) " : "Detected (${getDtNow()})"
		LogAction("Nest API Issues ${str}", "warn", true)
	}
	return result
}

String apiIssueDesc() {
	String res = "Good"
	//this looks at the last 3 items added and determines whether issue is sporadic or outage
	List t0 = []
	t0 = (List)state.apiIssuesList ?: [false, false, false, false, false, false, false]
	state.apiIssuesList = t0
	def items = t0[3..-1].findAll { it == true }
	//LogTrace("apiIssueDesc: items: $items  t0: $t0")
	if(items?.size() >= 1 && items?.size() <= 2) { res = "Sporadic Issues" }
	else if(items?.size() >= 3) { res = "Full Outage" }
	//log.debug "apiIssueDesc: $res"
	return res
}

static Integer issueListSize() { return 7 }

Integer getApiIssueSec() { return getTimeSeconds("apiIssueDt", 100000, "getApiIssueSec") }
Integer getLastApiIssueMsgSec() { return getTimeSeconds("lastApiIssueMsgDt", 100000, "getLastApiIssueMsgSec") }

private void apiIssueNotify() {
	if( (getApiIssueSec() > 600) && (getLastAnyCmdSentSeconds() > 600)) {
		updTimestampMap("apiIssueDt")
		state.apiIssuesList = []
		if((Boolean)state.apiRateLimited) {
			state.apiRateLimited = false
			LogAction("Clearing rate Limit", "info", true)
		}
	}

	if( !(getLastApiIssueMsgSec() > 900)) { return }
	Boolean rateLimit = (Boolean)state.apiRateLimited ? true : false
	Boolean apiIssue = apiIssues() ? true : false // any recent API issues
	if(apiIssue || rateLimit) {
		String msg = ""
		msg += apiIssue ? "\nThe Nest API appears to be having issues. This will effect the updating of device and location data.\nThe issues started at (${getTimestampVal("apiIssueDt")})" : ""
		msg += rateLimit ? "${apiIssue ? "\n\n" : "\n"}Your API connection is currently being Rate-limited for excessive commands." : ""
		if(sendMsg("${app.label} API Issue Warning", msg, 1)) {
			updTimestampMap("lastApiIssueMsgDt", getDtNow())
		}
	}
}

Integer getLastFailedCmdMsgSec() { return getTimeSeconds("lastFailedCmdMsgDt", 100000, "getLastFailedCmdMsgSec") }

private void failedCmdNotify(Map failData, String tstr) {
	if(!(getLastFailedCmdMsgSec() > 300)) { return }
	Boolean cmdFail = ((String)failData.msg != sNULL) ? true : false
	String cmdstr = tstr ?: (String)state.lastCmdSent
	String msg = "\nThe (${cmdstr}) CMD sent to the API has failed.\nStatus Code: ${failData.code}\nErrorMsg: ${failData.msg}\nDT: ${failData.dt}"
	if(cmdFail) {
		if(sendMsg(app.label+' API CMD Failed', msg, 1)) {
			updTimestampMap("lastFailedCmdMsgDt", getDtNow())
		}
	}
	LogAction(msg, (cmdFail ? "error" : "warn"), true)
}

private void apiIssueEvent(Boolean issue) {
	List list = (List)state.apiIssuesList ?: [false, false, false, false, false, false, false]
	Integer listSize = issueListSize()
	if(list.size() < listSize) {
		list.push(issue)
	}
	else if(list.size() > listSize) {
		Integer nSz = (list.size()-listSize) + 1
		List nList = list?.drop(nSz)
		nList?.push(issue)
		list = nList
	}
	else if(list.size() == listSize) {
		def nList = list.drop(1)
		nList?.push(issue)
		list = nList
	}
	state.apiIssuesList = list
	if(issue) {
		if(!getTimestampVal("apiIssueDt")) {
			updTimestampMap("apiIssueDt", getDtNow())
		}
	} else {
		def result = list[3..-1].every { it == false }
		Boolean rateLimit = ((Boolean)state.apiRateLimited) ? true : false
		if(rateLimit) {
			Integer t0 = state.apiCmdFailData?.dt ? GetTimeDiffSeconds((String)state.apiCmdFailData?.dt, sNULL, "apiIssueEvent").toInteger() : 200
			if((t0 > 120 && result) || t0 > 500) {
				state.apiRateLimited = false
				rateLimit = false
				LogAction("Clearing rate Limit", "info", true)
			}
		}
	}
}

private Boolean ok2PollMetaData() {
	return pollOk("Meta")
}

private Boolean ok2PollDevice() {
	return pollOk("Dev")
}

private Boolean ok2PollStruct() {
	return (pollOk("Str") || !state.structData) ? true : false
}

private Boolean pollOk(String typ) {
	if(getNestAuthToken()==sNULL) { return false }
	if((Boolean)state.pollBlocked) { return false }
	if((Boolean)state."need${typ}Poll") { return true }
	Integer pollTime = "${typ}Poll"() as Integer
	Integer val = pollTime / 3
	val = Math.max(Math.min(val.toInteger(), 50),25)
	return ( (("getLast${typ}PollSec"() + val) > pollTime) ? true : false )
}


private Boolean isPollAllowed() {
	return (state.pollingOn && getNestAuthToken()!=sNULL && ((List)settings.thermostats || (List)settings.protects || (List)settings.cameras || (Boolean)settings.presDevice)) ? true : false
}

Integer getLastMetaPollSec() { return getTimeSeconds("lastMetaDataUpd", 100000, "getLastMetaPollSec") }
Integer getLastDevPollSec() { return getTimeSeconds("lastDevDataUpd", 840, "getLastDevPollSec") }
Integer getLastStrPollSec() { return getTimeSeconds("lastStrDataUpd", 1000, "getLastStrPollSec") }
Integer getLastForcedPollSec() { return getTimeSeconds("lastForcePoll", 1000, "getLastForcedPollSec") }
Integer getLastChildUpdSec() { return getTimeSeconds("lastChildUpdDt", 100000, "getLastChildUpdSec") }
Integer getLastChildForceUpdSec() { return getTimeSeconds("lastChildForceUpdDt", 100000, "getLastChildForceUpdSec") }
Integer getLastHeardFromNestSec() { return getTimeSeconds("lastHeardFromNestDt", 100000, "getLastHeardFromNestSec") }

/************************************************************************************************
 |										Nest API Commands										|
 *************************************************************************************************/

private void cmdProcState(Boolean value) { atomicState.cmdIsProc = value }
private Boolean cmdIsProc() { return (!(Boolean)atomicState.cmdIsProc) ? false : true }
private Integer getLastProcSeconds() { return getTimeSeconds("cmdLastProcDt", 0, "getLastProcSeconds") }

static Map apiVar() {
	Map api = [
			rootTypes: [
					struct:"structures", cos:"devices/smoke_co_alarms", tstat:"devices/thermostats", cam:"devices/cameras", meta:"metadata"
			],
			cmdObjs: [
					targetF:"target_temperature_f", targetC:"target_temperature_c", targetLowF:"target_temperature_low_f", setLabel:"label",
					targetLowC:"target_temperature_low_c", targetHighF:"target_temperature_high_f", targetHighC:"target_temperature_high_c",
					fanActive:"fan_timer_active", fanTimer:"fan_timer_timeout", fanDuration:"fan_timer_duration", hvacMode:"hvac_mode",
					away:"away", streaming:"is_streaming", setTscale:"temperature_scale", eta:"eta"
			]
	]
	return api
}

// There are 3 different return values
def getPdevId(Boolean virt, String devId) {
	def pChild
	if(virt && state.vThermostats && devId) {
		if(state."vThermostat${devId}") {
			String pdevId = (String)state."vThermostatMirrorId${devId}"
			if(pdevId) { pChild = getChildDevice(pdevId) }
			if(pChild) { return pChild }
			else { return "00000" }
		}
	}
	return pChild
}

/*
void setEtaState(child, etaData, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? child?.toString() : child.device.deviceNetworkId.toString()

	String str1 = "setEtaState | "
	String strAction = "BAD data"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) | Trip_Id: ${etaData?.trip_id} | Begin: ${etaData?.estimated_arrival_window_begin} | End: ${etaData?.estimated_arrival_window_end}"

	if(etaData?.trip_id && etaData?.estimated_arrival_window_begin && etaData?.estimated_arrival_window_end) {
		def etaObj = [ "trip_id":"${etaData.trip_id}", "estimated_arrival_window_begin":"${etaData.estimated_arrival_window_begin}", "estimated_arrival_window_end":"${etaData.estimated_arrival_window_end}" ]
		// "trip_id":"sample-trip-id","estimated_arrival_window_begin":"2014-10-31T22:42:00.000Z","estimated_arrival_window_end":"2014-10-31T23:59:59.000Z"
		// new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

		strAction = "Setting Eta"
		def pChild = getPdevId(virtual, devId)
		if(pChild == null) {
			LogAction(str1+strAction+strArgs, "debug", true)
			Booelan a=sendNestCmd((String)state.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.eta, etaObj, devId)
			return
		} else {
			if(pChild != "00000") {
				LogAction(str1+strAction+strArgs, "debug", true)
				pChild.setNestEta(etaData?.trip_id, etaData?.estimated_arrival_window_begin, etaData.estimated_arrival_window_end) {
				}
				return
			} else {
				strAction = "CANNOT Set Eta"
			}
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
}

void cancelEtaState(child, trip_id, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? child?.toString() : child.device.deviceNetworkId.toString()

	String str1 = "cancelEtaState | "
	String strAction = "BAD data"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) | Trip_Id: ${trip_id}"

	if(trip_id) {
		def etaObj = [ "trip_id":"${trip_id}", "estimated_arrival_window_begin":0, "estimated_arrival_window_end":0 ]
		// "trip_id":"sample-trip-id","estimated_arrival_window_begin":"2014-10-31T22:42:00.000Z","estimated_arrival_window_end":"2014-10-31T23:59:59.000Z"
		// new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

		strAction = "Cancel Eta"
		def pChild = getPdevId(virtual, devId)
		if(pChild == null) {
			LogAction(str1+strAction+strArgs, "debug", true)
			Boolean a=sendNestCmd((String)state.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.eta, etaObj, devId)
			return
		} else {
			if(pChild != "00000") {
				LogAction(str1+strAction+strArgs, "debug", true)
				pChild.cancelNestEta(trip_id) {
				}
				return
			} else {
				strAction = "CANNOT Cancel Eta"
			}
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
}
 */

void setCamStreaming(child, streamOn) {
	String devId = !child?.device?.deviceNetworkId ? child?.toString() : child.device.deviceNetworkId.toString()
	Boolean val = streamOn.toBoolean() ? true : false
	LogAction("setCamStreaming | Setting Camera (${child?.device?.displayName} - ${devId}) Streaming to (${val ? "On" : "Off"})", "debug", true)
	Boolean a=sendNestCmd(devId, apiVar().rootTypes.cam, apiVar().cmdObjs.streaming, val, devId)
}

Boolean setStructureAway(child, value, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId
	Boolean val = value?.toBoolean()

	String str1 = "setStructureAway | "
	String strAction = ""
	strAction = "Setting Nest Location:"
	String strArgs = " (${child?.device?.displayName} ${!devId ? "" : "-  ${devId}"} to (${val ? "Away" : "Home"})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		if(val) {
			Boolean ret = sendNestCmd((String)state.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.away, "away", devId)
			// Below is to ensure automations read updated value even if queued
			if(ret && state.structData && (String)state.structures && state.structData[(String)state.structures]?.away) {
				def t0 = state.structData
				t0[(String)state.structures].away = "away"
				state.structData = t0
			}
			return ret
		}
		else {
			Boolean ret = sendNestCmd((String)state.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.away, "home", devId)
			if(ret && state.structData && (String)state.structures && state.structData[(String)state.structures]?.away) {
				Map t0 = (Map)state.structData
				t0[(String)state.structures].away = "home"
				state.structData = t0
			}
			return ret
		}
	} else {
		if(pChild != "00000") {
			LogAction(str1+strAction+strArgs, "debug", true)
			if(val) {
				pChild.away()
			} else {
				pChild.present()
			}
			return true
		} else {
			strAction = "CANNOT Set Location"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean setFanMode(child, fanOn, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId
	Boolean val = fanOn.toBoolean()

	String str1 = "setFanMode | "
	String strAction = ""
	strAction = "Setting"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Fan Mode to (${val ? "On" : "Auto"})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.fanActive, val, devId)
	} else {
		if(pChild != "00000") {
			LogAction(str1+strAction+strArgs, "debug", true)
			if(val) {
				pChild.fanOn()
			} else {
				pChild.fanAuto()
			}
			return true
		} else {
			strAction = "CANNOT Set"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean setHvacMode(child, String mode, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId

	String str1 = "setHvacMode | "
	String strAction = ""
	strAction = "Setting"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) HVAC Mode to (${mode})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.hvacMode, mode.toString(), devId)
	} else {
		if(pChild != "00000") {
			LogAction(str1+strAction+strArgs, "debug", true)
			switch (mode) {
				case "heat-cool":
					pChild.auto()
					break
				case "heat":
					pChild.heat()
					break
				case "cool":
					pChild.cool()
					break
				case "eco":
					pChild.eco()
					break
				case "off":
					pChild.off()
					break
				case "emergency heat":
					pChild.emergencyHeat()
					break
				default:
					LogAction("setHvacMode: Invalid Request: ${mode}", "warn", true)
					break
			}
			return true
		} else {
			strAction = "CANNOT Set "
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean setTargetTemp(child, String unit, temp, String mode, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId

	String str1 = "setTargetTemp | "
	String strAction = "Setting"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		if(unit == "C") {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetC, temp, devId)
		}
		else {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetF, temp, devId)
		}
	} else {
		LogAction(str1+strAction+strArgs, "debug", true)
		String appId = (String)state."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }
		if(automationChildApp) {
			Boolean res = automationChildApp.remSenTempUpdate(temp,mode)
			if(res) { return res }
		}
		if(pChild != "00000") {
			if(mode == 'cool') {
				pChild.setCoolingSetpoint(temp)
			} else if(mode == 'heat') {
				pChild.setHeatingSetpoint(temp)
			} else { LogAction("setTargetTemp - UNKNOWN MODE (${mode}) child ${pChild}", "warn", true); return false }
			return true
		} else {
			strAction = "CANNOT Set"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean setTargetTempLow(child, unit, temp, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId

	String str1 = "setTargetTempLow | "
	String strAction
	strAction = "Setting"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp Low to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		if(unit == "C") {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetLowC, temp, devId)
		}
		else {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetLowF, temp, devId)
		}
	} else {
		LogAction(str1+strAction+strArgs, "debug", true)
		String appId = (String)state."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }

		if(automationChildApp) {
			Boolean res = automationChildApp.remSenTempUpdate(temp,"heat")
			if(res) { return res }
		}
		if(pChild != "00000") {
			pChild.setHeatingSetpoint(temp)
			return true
		} else {
			strAction = "CANNOT Set"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean setTargetTempHigh(child, unit, temp, Boolean virtual=false) {
	String devId = !child?.device?.deviceNetworkId ? sNULL : (String)child.device.deviceNetworkId

	String str1 = "setTargetTempHigh | "
	String strAction = ""
	strAction = "Setting"
	String strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp High to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual, devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		if(unit == "C") {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetHighC, temp, devId)
		}
		else {
			return sendNestCmd(devId, apiVar().rootTypes.tstat, apiVar().cmdObjs.targetHighF, temp, devId)
		}
	} else {
		LogAction(str1+strAction+strArgs, "debug", true)
		String appId = (String)state."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }

		if(automationChildApp) {
			Boolean res = automationChildApp.remSenTempUpdate(temp,"cool")
			if(res) { return res }
		}
		if(pChild != "00000") {
			pChild.setCoolingSetpoint(temp)
			return true
		} else {
			strAction = "CANNOT Set"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
	return false
}

Boolean sendNestCmd(String cmdTypeId, String cmdType, String cmdObj, cmdObjVal, String childId) {
	// LogAction("sendNestCmd $cmdTypeId, $cmdType, $cmdObj, $cmdObjVal, $childId", "info", true)
	if(getNestAuthToken()==sNULL) {
		LogAction("sendNestCmd Error | Nest Auth Token Not Found", "warn", true)
		return false
	}
	try {
		if(cmdTypeId) {
			Integer qnum = getQueueNumber(cmdTypeId)
			if(qnum == -1 ) { return false }

			state.pollBlocked = true
			state.pollBlockedReason = "Sending Cmd"
			List cmdData = [cmdTypeId, cmdType, cmdObj, cmdObjVal, now()]

			List tempQueue = []
			List newCmd = []
			Boolean replaced = false
			Boolean skipped = false
			Boolean schedQ = false

			List cmdQueue = (List)atomicState."cmdQ${qnum}"
			if(cmdQueue == null) { cmdQueue = [] }
			cmdQueue.each { cmd ->
				if(newCmd != []) {
					tempQueue << newCmd
				}
				newCmd = [cmd[0], cmd[1], cmd[2], cmd[3], cmd[4]]
			}

			if(newCmd != []) {		// newCmd is last command in queue
				if((String)newCmd[1] == cmdType && (String)newCmd[2] == cmdObj && newCmd[3] == cmdObjVal) {	// Exact same command; leave it and skip
					skipped = true
					tempQueue << newCmd
				} else if((String)newCmd[1] == cmdType && (String)newCmd[2] == cmdObj &&
						(String)newCmd[2] != (String)apiVar().cmdObjs.away &&
						(String)newCmd[2] != (String)apiVar().cmdObjs.fanActive &&
						(String)newCmd[2] != (String)apiVar().cmdObjs.fanTimer && (String)newCmd[2] != (String)apiVar().cmdObjs.eta) {
					// if we are changing the same setting again use latest - this is Temp settings, hvac
					replaced = true
					tempQueue << cmdData
				} else {
					tempQueue << newCmd
					tempQueue << cmdData
				}
			} else {
				tempQueue << cmdData
			}
			atomicState."cmdQ${qnum}" = tempQueue

			String str = "Adding"
			if(replaced) { str = "Replacing" }
			if(skipped) { str = "Skipping" }

			if(replaced || skipped) {
				LogAction("Command Matches the Last item in Queue ${qnum} - ${str}", "warn", true)
			}

			LogAction("${str} Cmd to Queue [${qnum}] (Queued Items: ${tempQueue?.size()}): $cmdTypeId, $cmdType, $cmdObj, $cmdObjVal, $childId", "info", true)
			state.lastQcmd = cmdData
			schedNextWorkQ()
			return true

		} else {
			LogAction("sendNestCmd null cmdTypeId $cmdTypeId, $cmdType, $cmdObj, $cmdObjVal, $childId", "warn", true)
			return false
		}
	}
	catch (ex) {
		log.error "sendNestCmd Exception: ${ex?.message}"
		return false
	}
}

/*
 * Each nest device has its own queue (as does the nest structure itself)
 *   Queues are "assigned" dynamically as they are needed
 * Each queue has it own "free" command counts, then commands are limited to 1 per minute.
 */
private Integer getQueueNumber(String cmdTypeId) {
	List t0=(List)atomicState.cmdQlist
	List cmdQueueList = t0 ?: []
	if(t0==null) atomicState.cmdQlist = cmdQueueList
	Integer qnum = cmdQueueList.indexOf(cmdTypeId)
	if(qnum == -1) {
// need semaphore
		cmdQueueList = (List)atomicState.cmdQlist
		cmdQueueList << cmdTypeId
		atomicState.cmdQlist = cmdQueueList
		qnum = cmdQueueList.indexOf(cmdTypeId)
		atomicState."cmdQ${qnum}" = null
		setLastCmdSentSeconds(qnum, sNULL)
	}
	qnum = cmdQueueList.indexOf(cmdTypeId)
	if(qnum == -1 || qnum == null) { LogAction("getQueueNumber: NOT FOUND", "warn", true ) }
	else {
		if(getLastCmdSentSeconds(qnum) > 3600) { setRecentSendCmd(qnum, cmdMaxVal()) } // if nothing sent in last hour, reset command limit
	}
	return qnum
}

/*
 * Queues are processed in the order in which commands were sent (across all queues)
 * This maintains proper state ordering for changes, as commands can have dependencies in order
 */
Integer getQueueToWork() {
	Integer qnum
	Long savedtim
	List t0=(List)atomicState.cmdQlist
	List cmdQueueList = t0 ?: []
	if(t0==null) atomicState.cmdQlist = cmdQueueList
	cmdQueueList.eachWithIndex { val, idx ->
		List cmdQueue = (List)atomicState."cmdQ${idx}"
		if(cmdQueue?.size() > 0) {
			def cmdData = cmdQueue[0]
			Long timVal = (Long)cmdData[4]
			if(savedtim == null || timVal < savedtim) {
				savedtim = timVal
				qnum = idx
			}
		}
	}
	// LogTrace("getQueueToWork queue: ${qnum}")
	if(qnum != -1 && qnum != null) {
		if(getLastCmdSentSeconds(qnum) > 3600) { setRecentSendCmd(qnum, cmdMaxVal()) } // if nothing sent in last hour, reset command limit
	}
	return qnum
}

private static Integer cmdMaxVal() { return 2 }

void schedNextWorkQ(Boolean useShort=false) {
	Integer cmdDelay = getChildWaitVal()
	if(useShort) { cmdDelay = 0 }
	//
	// This is throttling the rate of commands to the Nest service for this access token.
	// If too many commands are sent Nest throttling could shut all write commands down for 1 hour to the device or structure
	// This allows up to 3 commands if none sent in the last hour, then only 1 per 60 seconds. Nest could still
	// throttle this if the battery state on device is low.
	// https://nestdevelopers.io/t/user-receiving-exceeded-rate-limit-on-requests-please-try-again-later/354
	//

	Integer qnum = getQueueToWork()
	Integer timeVal = cmdDelay
	String str = ""
	Integer queueItemsAvail = 0
	Integer lastCommandSent = 0
	if(qnum != null) {
		queueItemsAvail = getRecentSendCmd(qnum)
		lastCommandSent = getLastCmdSentSeconds(qnum)
		if( (queueItemsAvail == 0 && lastCommandSent > 60) ) { queueItemsAvail = 1 }
		if( queueItemsAvail <= 0 || (Boolean)state.apiRateLimited) {
			timeVal = 60 + cmdDelay
		} else if(lastCommandSent < 60) {
			timeVal = (60 - lastCommandSent + cmdDelay)
			if(queueItemsAvail > 0) { timeVal = 0 }
		}
		str = timeVal > cmdDelay || (Boolean)state.apiRateLimited ? "*RATE LIMITING ON* " : ""
		//LogAction("schedNextWorkQ │ ${str}queue: ${qnum} │ schedTime: ${timeVal} │ recentSendCmd: ${queueItemsAvail} │ last seconds: ${lastCommandSent} │ cmdDelay: ${cmdDelay} | runInActive: ${atomicState.workQrunInActive} | Api Limited: ${(Boolean)state.apiRateLimited}", "info", true)
	} else {
		return //timeVal = 0
	}
	String actStr = "ALREADY PENDING "
	if(cmdIsProc()) { actStr = "COMMAND RUNNING " }
	if(!(Boolean)atomicState.workQrunInActive && !cmdIsProc() ) {
		atomicState.workQrunInActive = true
		if(timeVal != 0) {
			actStr = "RUNIN "
			runIn(timeVal.toInteger(), "workQueue", [overwrite: true])
		} else {
			actStr = "DIRECT CALL "
			workQueue()
		}
	}
	LogAction("schedNextWorkQ ${actStr} │ ${str}queue: ${qnum} │ schedTime: ${timeVal} │ recentSendCmd: ${queueItemsAvail} │ last seconds: ${lastCommandSent} │ cmdDelay: ${cmdDelay} | runInActive: ${atomicState.workQrunInActive} | command proc: ${cmdIsProc()} | Api Limited: ${(Boolean)state.apiRateLimited}", "info", true)
}

private Integer getRecentSendCmd(Integer qnum) {
	return atomicState."recentSendCmd${qnum}"
}

private void setRecentSendCmd(Integer qnum, Integer val) {
	atomicState?."recentSendCmd${qnum}" = val
}

def sendEcoActionDescToDevice(dev, desc) {
	if(dev && desc) {
		dev?.ecoDesc(desc)
	}
}

private Integer getLastAnyCmdSentSeconds() { return getTimeSeconds("lastCmdSentDt", 3601, "getLastAnyCmdSentSeconds") }
private Integer getLastCmdSentSeconds(Integer qnum) { return getTimeSeconds("lastCmdSentDt${qnum}", 3601, "getLastCmdSentSeconds") }

private void setLastCmdSentSeconds(Integer qnum, String val) {
	updTimestampMap("lastCmdSentDt${qnum}", val)
	updTimestampMap("lastCmdSentDt", val)
}

/*
void storeLastCmdData(cmd, qnum) {
	if(cmd) {
		def newVal = ["qnum":qnum, "obj":cmd[2], "value":cmd[3], "date":getDtNow()]

		def list = state.cmdDetailHistory ?: []
		Integer listSize = 30
		if(list?.size() < listSize) {
			list.push(newVal)
		}
		else if(list?.size() > listSize) {
			Integer nSz = (list?.size()-listSize) + 1
			def nList = list?.drop(nSz)
			nList?.push(newVal)
			list = nList
		}
		else if(list?.size() == listSize) {
			def nList = list?.drop(1)
			nList?.push(newVal)
			list = nList
		}
		if(list) { state.cmdDetailHistory = list }
	}
}
*/

void workQueue() {
	LogTrace("workQueue")
	atomicState.workQrunInActive = false
	//def cmdDelay = getChildWaitVal()
	List t0=(List)atomicState.cmdQlist
	List cmdQueueList = t0 ?: []
	if(t0==null) atomicState.cmdQlist = cmdQueueList

	Integer qnum = getQueueToWork()
	if(qnum == null) { qnum = 0 }

	t0=(List)atomicState."cmdQ${qnum}"
	List cmdQueue = t0 ?: []
	if(t0==null) atomicState."cmdQ${qnum}" = cmdQueue

	try {
		if(cmdQueue?.size() > 0) {
			LogTrace("workQueue │ Run Queue: ${qnum}")
			runIn(90, "workQueue", [overwrite: true])  //lost schedule catchall
			if(!cmdIsProc()) {
				cmdProcState(true)
				state.pollBlocked = true
				state.pollBlockedReason = "Processing Queue"
				cmdQueue = (List)atomicState."cmdQ${qnum}"
				// log.trace "cmdQueue(workqueue): $cmdQueue"
				def cmd = cmdQueue?.remove(0)
				// log.trace "cmdQueue(workqueue-after): $cmdQueue"
				// log.debug "cmd: $cmd"
				atomicState."cmdQ${qnum}" = cmdQueue
				Boolean cmdres

				if(getLastCmdSentSeconds(qnum) > 3600) { setRecentSendCmd(qnum, cmdMaxVal()) } // if nothing sent in last hour, reset command limit

				// storeLastCmdData(cmd, qnum)

				if((String)cmd[1] == "poll") {
					state.needStrPoll = true
					state.needDevPoll = true
					state.forceChildUpd = true
					cmdres = true
				} else {
					//cmdres = procNestCmd(getNestApiUrl(), cmd[0], cmd[1], cmd[2], cmd[3], qnum)
					cmdres = queueProcNestCmd(getNestApiUrl(), (String)cmd[0], (String)cmd[1], (String)cmd[2], cmd[3], qnum, cmd)
					return
				}
				finishWorkQ(cmd, cmdres)
			} else { LogAction("workQueue: busy processing command", "warn", true) }
		} else { state.pollBlocked = false; state.remove("pollBlockedReason"); cmdProcState(false) }
	}
	catch (ex) {
		log.error "workQueue Exception Error: ${ex?.message}"

		finishERR()
	}
}

void finishERR(){
	cmdProcState(false)
	state.needDevPoll = true
	state.needStrPoll = true
	state.forceChildUpd = true
	state.pollBlocked = false
	state.remove("pollBlockedReason")
	atomicState.workQrunInActive = true
	runIn(60, "workQueue", [overwrite: true])
	runIn(64, "postCmd", [overwrite: true])
}

void finishWorkQ(List cmd, Boolean result) {
	LogTrace("finishWorkQ cmd: $cmd  result: $result")
	Integer cmdDelay = getChildWaitVal()

	if( !result ) {
		state.forceChildUpd = true
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		runIn((cmdDelay * 3).toInteger(), "postCmd", [overwrite: true])
	}

	state.needDevPoll = true
	if(cmd && (String)cmd[1] == (String)apiVar().rootTypes.struct) {
		state.needStrPoll = true
		state.forceChildUpd = true
	}

	updTimestampMap("cmdLastProcDt", getDtNow())
	cmdProcState(false)

	Integer qnum = getQueueToWork()
	if(qnum == null) { qnum = 0 }
	if(!atomicState?."cmdQ${qnum}") { atomicState?."cmdQ${qnum}" = [] }

	List cmdQueue = (List)atomicState."cmdQ${qnum}"
	if(cmdQueue?.size() == 0) {
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		state.needChildUpd = true
		runIn(cmdDelay, "postCmd", [overwrite: true])
	}
	else { schedNextWorkQ(true) }

	if(cmdQueue?.size() > 10) {
		sendMsg("Warning", "There is now ${cmdQueue?.size()} events in the Command Queue. Something must be wrong", 1)
		LogAction("${cmdQueue?.size()} events in the Command Queue", "warn", true)
	}
	//return
}

Boolean queueProcNestCmd(String uri, String typeId, String type, String obj, objVal, Integer qnum, cmd, Boolean redir = false) {
	String myStr = "queueProcNestCmd"
	LogTrace("${myStr}: typeId: ${typeId}, type: ${type}, obj: ${obj}, objVal: ${objVal}, qnum: ${qnum}, isRedirUri: ${redir}")

	Boolean result = false
	String tok=getNestAuthToken()
	if(tok==sNULL) { return result }

	try {
		if(getLastAnyCmdSentSeconds() > 120) {
			state.nestRedirectUrl = sNULL
			state.remove("nestRedirectUrl")  // don't cache the redirect URL too long
		}

		String url = (!redir && (String)state.nestRedirectUrl) ? (String)state.nestRedirectUrl : uri
		//String url = uri
		String urlPath = "/${type}/${typeId}".toString()
		def data = new JsonBuilder("${obj}":objVal)
		def params = [
			uri: url,
			path: urlPath,
			requestContentType: "application/json",
			headers: [
				"Content-Type": "application/json",
				"Authorization": "Bearer ${tok}".toString()
			],
			body: data.toString()
		]
/*		//def urlPath
		if((uri || (String)state.nestRedirectUrl) && !redir) {
			//urlPath = "/${type}/${typeId}"
			params.path = "/${type}/${typeId}".toString()
		}*/

		LogTrace("${myStr} Url: $url | params: ${params}")
		LogAction("Processing Queued Cmd: [ObjId: ${typeId} | ObjType: ${type} | ObjKey: ${obj} | ObjVal: ${objVal} | QueueNum: ${qnum} | Redirect: ${redir}]", "trace", true)
		state.lastCmdSent = "$type: (${objKey}: ${objVal})".toString()

		adjThrottle(qnum, redir)

		def t0 = objVal
//		if(t0 instanceof Map) { t0 = [:] + objVal }
		def asyncargs = [
			typeId: typeId,
			type: type,
			obj: obj,
			objVal: t0,
			qnum: qnum,
			cmd: cmd ]

		asynchttpPut('nestCmdResponse', params, asyncargs)

	} catch(ex) {
		log.error "${myStr} (command: $cmd) Exception: ${ex?.message}"//, ex
	}
}

void nestCmdResponse(resp, data) {
	LogAction("nestCmdResponse(${data?.cmd})", "info", false)
	String typeId = (String)data?.typeId
	String type = (String)data?.type
	String obj = (String)data?.obj
	def objVal = data?.objVal
	if(objVal instanceof Map) { objVal = [:] + data?.objVal }
	Integer qnum = (Integer)data?.qnum
	def command = data?.cmd
	Boolean result = false
	String msg="nestCmdResponse | Processed Queue: ${qnum} | Obj: ($type{$obj:$objVal})".toString()
	try {
		if(!command) { cmdProcState(false); return }

		if(resp?.status == 307) {
			String redirUrl = resp?.headers?.Location
			URI newUri = new URI(redirUrl)
			String newUrl = "${newUri?.getScheme()}://${newUri?.getHost()}:${newUri?.getPort()}".toString()
			LogTrace(msg+' REDIRECTED! to '+newUrl)
			if((newUrl != sNULL && newUrl.startsWith("https://")) && (!(String)state.nestRedirectUrl || (String)state.nestRedirectUrl != newUrl)) {
				state.nestRedirectUrl = newUrl
				Boolean a=queueProcNestCmd(newUrl, typeId, type, obj, objVal, qnum, command, true)
				return
			} else { LogAction("did not REDIRECT", "error", true) }
/*
			//LogTrace("resp: ${resp.headers}")
			def newUrl = resp?.headers?.Location?.split("\\?")
			//LogTrace("NewUrl: ${newUrl[0]}")
			queueProcNestApiCmd(newUrl[0], typeId, type, obj, objVal, qnum, command, true)
			return
*/
		}
		if(resp?.status == 200) {
			LogAction(msg+' SUCCESSFULLY!', "info", true)
			apiIssueEvent(false)
			state.lastCmdSentStatus = "ok"
			//atomicState.apiRateLimited = false
			//atomicState.apiCmdFailData = null
			result = true
		}
/*
		if(resp?.status == 429) {
			// requeue command
			def newCmd = [command[0], command[1], command[2], command[3], command[4]]
			def tempQueue = []
			tempQueue << newCmd
			if(!(List)atomicState."cmdQ${qnum}" ) { atomicState."cmdQ${qnum}" = [] }
			List cmdQueue = (List)atomicState."cmdQ${qnum}"
			cmdQueue.each { cmd ->
				newCmd = [cmd[0], cmd[1], cmd[2], cmd[3], cmd[4]]
				tempQueue << newCmd
			}
			atomicState."cmdQ${qnum}" = tempQueue
		}
*/
		if(resp?.status != 200) {
			state.lastCmdSentStatus = "failed"
			state.remove("nestRedirectUrl")
			if(resp?.hasError()) {
				apiRespHandler((resp?.getStatus() ?: null), (resp?.getErrorJson() ?: null), "nestCmdResponse", msg, true)
				//apiRespHandler(resp?.status, resp?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
			} else {
				LogAction(msg+' could not process error', "error", true)
			}
			apiIssueEvent(true)
/*
			atomicState.lastCmdSentStatus = "failed"
			if(resp?.hasError()) {
				apiRespHandler((resp?.getStatus() ?: null), (resp?.getErrorJson() ?: null), "nestCmdResponse", msg, true)
			}
			apiIssueEvent(true)
*/
		}
/*
		if(resp?.status == 429) {
			result = true // we requeued the command
		}
*/
		finishWorkQ(command, result)

	} catch (ex) {
		state.lastCmdSentStatus = "failed"
		state.remove("nestRedirectUrl")

		finishERR()

		if(resp?.hasError()) {
			apiRespHandler((resp?.getStatus() ?: null), (/*resp?.getErrorJson() ?:*/ null), "nestCmdResponse Exception", msg, true)
		}
		apiIssueEvent(true)
		log.error msg+" (command: $command) Exception: ${ex?.message}"//, ex
	}
}

/*

def procNestCmd(uri, typeId, type, objKey, objVal, qnum, redir = false) {
	def result = false
	if(!getNestAuthToken()) { return result }
	try {
		if(getLastAnyCmdSentSeconds() > 120) {
			state.nestRedirectUrl = sNULL
			state.remove("nestRedirectUrl")  // don't cache the redirect URL too long
		}

		def url = (!redir && (String)state.nestRedirectUrl) ? (String)state.nestRedirectUrl : uri
		def data = new JsonBuilder([(objKey):objVal])
		def params = [
				uri: url,
				contentType: "application/json",
				headers: [
						"Authorization": "Bearer ${getNestAuthToken()}"
				],
				body: data?.toString()
		]
		if((uri || (String)state.nestRedirectUrl) && !redir) {
			params["path"] = "/${type}/${typeId}"
		}
		state.lastCmdSent = "$type: (${objKey}: ${objVal})"

		adjThrottle(qnum, redir)

		// LogTrace("procNestCmd time update recentSendCmd:  ${getRecentSendCmd(qnum)}  last seconds:${getLastCmdSentSeconds(qnum)} queue: ${qnum}")

		httpPut(params) { resp ->
			if(resp?.status == 307) {
				def redirUrl = resp?.headers?.location
				def newUri = new URI(redirUrl?.toString())
				def newUrl = "${newUri?.getScheme()}://${newUri?.getHost()}:${newUri?.getPort()}"
				if((newUrl != null && newUrl.startsWith("https://")) && (!(String)state.nestRedirectUrl || (String)state.nestRedirectUrl != newUrl)) {
					state.nestRedirectUrl = newUrl
					if( procNestCmd(redirUrl, typeId, type, objKey, objVal, qnum, true) ) {
						return true
					}
				}
			}
			else if(resp?.status == 200) {
				LogAction("procNestCmd Processed Queue(${qnum}) Item: ($type{$objKey:$objVal}) SUCCESSFULLY!", "info", true)
				apiIssueEvent(false)
				state.lastCmdSentStatus = "ok"
				//state.apiRateLimited = false
				//state.apiCmdFailData = null
				result = true
			}
			else {
				state.lastCmdSentStatus = "failed"
				state.remove("nestRedirectUrl")
				apiRespHandler(resp?.status, resp?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
				apiIssueEvent(true)
			}
		}
	} catch (ex) {
		state.lastCmdSentStatus = "failed"
		state.remove("nestRedirectUrl")
		cmdProcState(false)
		if(ex instanceof groovyx.net.http.HttpResponseException && ex?.response) {
			apiRespHandler(ex?.response?.status, ex?.response?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
		} else {
			log.error "procNestCmd Exception: ($type | $objKey:$objVal) | Message: ${ex?.message}"
		}
		apiIssueEvent(true)
	}
	return result
}
*/

void adjThrottle(Integer qnum, Boolean redir) {
	if(!redir) {
		Integer t0 = getRecentSendCmd(qnum)
		Integer val = t0
		if(t0 > 0 /* && (getLastCmdSentSeconds(qnum) < 60) */ ) {
			val -= 1
		}
		Integer t1 = getLastCmdSentSeconds(qnum)
		if(t1 > 120 && t1 < 60*45 && val < (cmdMaxVal()-1) ) {
			val += 1
		}
		if(t1 > 60*30 && t1 < 60*45 && val < cmdMaxVal() ) {
			val += 1
		}
		LogTrace("adjThrottle orig recentSendCmd: ${t0} | new: ${val} | last seconds: ${t1} queue: ${qnum}")
		setRecentSendCmd(qnum, val)
	}
	setLastCmdSentSeconds(qnum, getDtNow())
}

void apiRespHandler(code, errJson, String methodName, String tstr=sNULL, Boolean isCmd=false) {
	// LogAction("[$methodName] | Status: (${code}) | Error Message: ${errJson}", "warn", true)
	if(!(code?.toInteger() in [200, 307])) {
		String result = sNULL
		Boolean notif = true
		String errMsg = errJson?.message != sNULL ? (String)errJson?.message : sNULL
		switch(code) {
			case 400:
				result = !errMsg ? "A Bad Request was made to the API..." : errMsg
				break
			case 401:
				result =  !errMsg ? "Authentication ERROR, Please try refreshing your login under Authentication settings..." : errMsg
				//revokeNestToken()
				break
			case 403:
				result =  !errMsg ? "Forbidden: Your Login Credentials are Invalid..." : errMsg
				//revokeNestToken()
				break
			case 429:
				result =  !errMsg ? "Requests are currently being blocked because of API Rate Limiting..." : errMsg
				state.apiRateLimited = true
				break
			case 500:
				result =  !errMsg ? "Internal Nest Error:" : errMsg
				notif = false
				break
			case 503:
				result =  !errMsg ? "There is currently a Nest Service Issue..." : errMsg
				notif = false
				break
			default:
				result =  !errMsg ? "Received Response..." : errMsg
				notif = false
				break
		}
		def failData = ["code":code, "msg":result, "method":methodName, "dt":getDtNow(), "isCmd": isCmd]
		state.apiCmdFailData = failData
		if(notif || isCmd) {
			failedCmdNotify(failData, tstr)
		}
		LogAction("$methodName error - (Status: $code - $result) - [ErrorLink: ${errJson?.type}] ${errJson?.error} ${errJson?.details}", "error", true)
	}
}


String getNestZipCode() {
	String tt = getStrucVal("postal_code")
	return tt ?: ""
}

String getNestTimeZone() {
	return getStrucVal("time_zone")
}

String getEtaBegin() {
	return getStrucVal("eta_begin")
}

String getPeakStart() {
	return getStrucVal("peak_period_start_time")
}

String getPeakEnd() {
	return getStrucVal("peak_period_end_time")
}

String getSecurityState() {
	return getStrucVal("wwn_security_state")
}

String getLocationPresence() {
	return getStrucVal("away")
}

String getStrucVal(String svariable) {
	def sData = state.structData
	String sKey = (String)state.structures
	def asStruc = sData && sKey && sData[sKey] ? sData[sKey] : null
	String retVal = asStruc ? asStruc[svariable] ?: sNULL : sNULL
	return (retVal != sNULL) ? retVal.toString() : sNULL
}

String getStZipCode() { return location?.zipCode?.toString() }

static Integer getChildWaitVal() { return tempChgWaitVal() }

/************************************************************************************
 |	This Section Discovers all structures and devices on your Nest Account.			|
 |	It also Adds Removes Devices from Hubitat										|
 ************************************************************************************/

private Map getNestStructures() {
	//LogTrace("Getting Nest Structures")
	Map mstruct = [:]
	Map thisstruct = [:]
	try {
		if(ok2PollStruct()) { getApiData("str") }
		if(state.structData) {
			def structs = state.structData
			structs?.eachWithIndex { struc, index ->
				String strucId = struc?.key
				def strucData = struc?.value

				String dni = [strucData.structure_id].join('.')
				mstruct[dni] = (String)strucData?.name

				if((String)strucData?.structure_id == (String)settings.structures) {
					thisstruct[dni] = (String)strucData?.name
				} else {
					if((String)state.structures) {
						if((String)strucData?.structure_id == (String)state.structures) {
							thisstruct[dni] = (String)strucData?.name
						}
					} else {
						if(!(String)settings.structures) {
							thisstruct[dni] = (String)strucData?.name
						}
					}
				}
			}
/*			if((Map)state.thermostats || (Map)state.protects || (Map)state.cameras || (Boolean)state.presDevice || (Map)state.vThermostats) {   // if devices are configured, you cannot change the structure until they are removed
				mstruct = thisstruct
			}*/
			if(ok2PollDevice()) { getApiData("dev") }
		} else { LogAction("Missing: structData  ${state.structData}", "warn", true) }

	} catch (ex) {
		log.error "getNestStructures Exception: ${ex?.message}"
	}
	return mstruct
}

private Map getNestThermostats() {
	//LogTrace("Getting Thermostat list")
	Map stats = [:]
	Map tstats = deviceDataFLD?.thermostats
	//LogTrace("Found ${tstats?.size()} Thermostats")
	tstats.each { stat ->
//		String statId = stat?.key
		def statData = stat?.value
		if(statData?.structure_id == (String)settings.structures) {
			String adni = [statData?.device_id].join('.')
			stats[adni] = getThermostatDisplayName(statData)
		}
	}
	return stats
}

private Map getNestProtects() {
	//LogTrace("Getting Nest Protect List")
	Map protects = [:]
	Map nProtects = deviceDataFLD?.smoke_co_alarms
	//LogTrace("Found ${nProtects?.size()} Nest Protects")
	nProtects.each { dev ->
//		String devId = dev?.key
		def devData = dev?.value
		if(devData?.structure_id == (String)settings.structures) {
			String bdni = [devData?.device_id].join('.')
			protects[bdni] = getProtectDisplayName(devData)
		}
	}
	return protects
}

private Map getNestCameras() {
	//LogTrace("Getting Nest Camera List")
	Map cameras = [:]
	Map nCameras = deviceDataFLD?.cameras
	//LogTrace("Found ${nCameras?.size()} Nest Cameras")
	nCameras.each { dev ->
//		String devId = dev?.key
		def devData = dev?.value
		if(devData?.structure_id == (String)settings.structures) {
			String bdni = [devData?.device_id].join('.')
			cameras[bdni] = getCameraDisplayName(devData)
		}
	}
	return cameras
}

private Map statState(List val) {
	Map stats = [:]
	Map tstats = getNestThermostats()
	tstats.each { stat ->
		String statId = stat?.key
		val.each { st ->
			if(statId == st) {
				String adni = [statId].join('.')
				stats[adni] = stat.value
			}
		}
	}
	return stats
}

private Map coState(List val) {
	Map protects = [:]
	Map nProtects = getNestProtects()
	nProtects.each { dev ->
		val.each { pt ->
			String devId = dev.key
			if(devId == pt) {
				String bdni = [devId].join('.')
				protects[bdni] = dev.value
			}
		}
	}
	return protects
}

private Map camState(List val) {
	Map cams = [:]
	Map nCameras = getNestCameras()
	nCameras.each { dev ->
		val.each { cm ->
			String devId = dev.key
			if(devId == cm) {
				String bdni = [devId].join('.')
				cams[bdni] = dev.value
			}
		}
	}
	return cams
}

static String getThermostatDisplayName(stat) {
	if((String)stat.name) { return (String)stat.name }
	else if((String)stat.name_long) { return (String)stat.name_long }
	else { return "Thermostatnamenotfound" }
}

static String getProtectDisplayName(prot) {
	if((String)prot.name) { return (String)prot.name }
	else if((String)prot.name_long) { return (String)prot.name_long }
	else { return "Protectnamenotfound" }
}

static String getCameraDisplayName(cam) {
	if((String)cam.name) { return (String)cam.name }
	else if((String)cam.name_long) { return (String)cam.name_long }
	else { return "Cameranamenotfound" }
}

String getNestDeviceDni(dni, String type) {
	//LogTrace("getNestDeviceDni: $dni | $type")
	String t1=(String)dni.key
	String retVal = t1
	def d1 = getChildDevice(t1)
	if(!d1) {
		String t0 = "Nest${type}-${dni.value.toString()} | ${t1}"
		d1 = getChildDevice(t0)
		if(d1) { retVal = t0 }
	}
	return retVal
}

String getNestTstatDni(dni) { return getNestDeviceDni(dni, "Thermostat") }

String getNestvStatDni(dni) { return getNestDeviceDni(dni, "vThermostat") }

String getNestProtDni(dni) { return getNestDeviceDni(dni, "Protect") }

String getNestCamDni(dni) { return getNestDeviceDni(dni, "Cam") }

String getNestPresId() {
	String dni = "Nest Presence Device" // old name 1
	def d3 = getChildDevice(dni)
	if(d3) { return dni }
	else {
		String stStruc=(String)state.structures
		if(stStruc) {
			dni = 'NestPres'+stStruc // old name 2
			d3 = getChildDevice(dni)
			if(d3) { return dni }
		}
		String retVal = ""
		if(stStruc) { retVal = 'NestPres | '+stStruc }
		else if((String)settings.structures) { retVal = 'NestPres | '+(String)settings.structures }
		else {
			LogAction('getNestPresID No structures '+stStruc, "warn", true)
			return ""
		}
		return retVal
	}
}

String getDefaultLabel(String ttype, String name) {
	//LogTrace("getDefaultLabel: ${ttype} ${name}")
	String defName=""
	if(name == sNULL || name == "") {
		LogAction("BAD CALL getDefaultLabel: ${ttype}, ${name}", "error", true)
	}else {
		switch (ttype) {
		case "thermostat":
			defName = "Nest Thermostat - ${name}"
			if((Boolean)state.devNameOverride && (Boolean)state.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "protect":
			defName = "Nest Protect - ${name}"
			if((Boolean)state.devNameOverride && (Boolean)state.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "camera":
			defName = "Nest Camera - ${name}"
			if((Boolean)state.devNameOverride && (Boolean)state.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "vthermostat":
			defName = "Nest vThermostat - ${name}"
			if((Boolean)state.devNameOverride && (Boolean)state.useAltNames) { defName = "${location.name} - Virtual ${name}" }
			break
		case "presence":
			defName = "Nest Presence Device"
			if((Boolean)state.devNameOverride && (Boolean)state.useAltNames) { defName = "${location.name} - Nest Presence Device" }
			break
		default:
			LogAction("BAD CALL getDefaultLabel: ${ttype}, ${name}", "error", true)
		}
	}
	return defName
}

String getNestTstatLabel(String name, String key) {
	//LogTrace("getNestTstatLabel: ${name}")
	String defName = getDefaultLabel("thermostat", name)
	if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
		return settings."tstat_${key}_lbl" ?: defName
	}
	return defName
}

String getNestProtLabel(String name, String key) {
	String defName = getDefaultLabel("protect", name)
	if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
		return settings."prot_${key}_lbl" ?: defName
	}
	return defName
}

String getNestCamLabel(String name, String key) {
	String defName = getDefaultLabel("camera", name)
	if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
		return settings."cam_${key}_lbl" ?: defName
	}
	return defName
}

String getNestVtstatLabel(String name, String key) {
	String defName = getDefaultLabel("vthermostat", name)
	if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
		return settings."vtstat_${key}_lbl" ?: defName
	}
	return defName
}

String getNestPresLabel() {
	String defName = getDefaultLabel("presence", "name")
	if((Boolean)state.devNameOverride && (Boolean)state.custLabelUsed) {
		return settings.presDev_lbl ? settings.presDev_lbl.toString() : defName
	}
	return defName
}

String getChildDeviceLabel(String dni) {
	if(!dni) { return sNULL }
	def t0 = getChildDevice(dni)
	return t0?.getLabel() ?: sNULL
}

Boolean addRemoveDevices(Boolean uninst=false) {
	LogTrace("addRemoveDevices")
	Boolean retVal = false
	try {
		def devsInUse = []
		def tstats
		def nProtects
		def nCameras
		def nVstats
		Integer devsCrt = 0
		Integer presCnt = 0
		Integer streamCnt = 0
		Boolean noCreates = true
		Boolean noDeletes = true

		if(!uninst) {
			if((Map)state.thermostats) {
				tstats = state.thermostats?.collect { dni ->
					def d1 = getChildDevice(getNestTstatDni(dni))
					if(!d1) {
						String d1Label = getNestTstatLabel("${dni?.value}", (String)dni.key)
						d1 = addChildDevice(namespace(), getThermostatChildName(), (String)dni.key, [label: d1Label])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d1.displayName} with (Id: ${(String)dni.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d1.displayName} with (Id: ${(String)dni.key})", "debug", true)
					}
					devsInUse += (String)dni.key
				}
			}

			if((Map)state.protects) {
				nProtects = state.protects?.collect { dni ->
					def d2 = getChildDevice(getNestProtDni(dni))
					if(!d2) {
						String d2Label = getNestProtLabel("${dni.value}", (String)dni.key)
						d2 = addChildDevice(namespace(), getProtectChildName(), (String)dni.key, [label: d2Label])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d2.displayName} with (Id: ${(String)dni.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d2.displayName} with (Id: ${(String)dni.key})", "debug", true)
					}
					devsInUse += (String)dni.key
				}
			}

			if((Boolean)state.presDevice) {
				try {
					String dni = getNestPresId()
					def d3 = getChildDevice(dni)
					if(!d3) {
						String d3Label = getNestPresLabel()
						d3 = addChildDevice(namespace(), getPresenceChildName(), dni, [label: d3Label])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d3.displayName} with (Id: ${dni})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d3.displayName} with (Id: ${dni})", "debug", true)
					}
					devsInUse += dni
				} catch (ex) {
					LogAction("Nest Presence Device Handler may not be installed/published", "warn", true)
					noCreates = false
				}
				presCnt = 1
			}

			if((Map)state.cameras) {
				nCameras = state.cameras?.collect { dni ->
					def d4 = getChildDevice(getNestCamDni(dni))
					if(!d4) {
						String d4Label = getNestCamLabel("${dni.value}", (String)dni.key)
						d4 = addChildDevice(namespace(), getCameraChildName(), (String)dni.key, [label: d4Label])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d4.displayName} with (Id: ${(String)dni.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d4.displayName} with (Id: ${(String)dni.key})", "debug", true)
					}
					devsInUse += (String)dni.key
				}
			}

			if((Map)state.vThermostats) {
				nVstats = state.vThermostats.collect { dni ->
					//LogAction("state.vThermostats: ${state.vThermostats} dni: ${dni} dni.key: ${(String)dni.key} dni.value: ${dni.value.toString()}", "debug", true)
					def d6 = getChildDevice(getNestvStatDni(dni))
					if(!d6) {
						String d6Label = getNestVtstatLabel("${dni.value}", (String)dni.key)
						//LogAction("CREATED: ${d6Label} with (Id: ${dni.key})", "debug", true)
						d6 = addChildDevice(namespace(), getThermostatChildName(), (String)dni.key, [label: d6Label, "data":["isVirtual":"true"]])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d6.displayName} with (Id: ${(String)dni.key})", "debug", true)
					} else {
						LogAction("Found: ${d6.displayName} with (Id: ${(String)dni.key}) exists", "debug", true)
					}
					devsInUse += (String)dni.key
					return d6
				}
			}


			if(restEnabled()) {
				def d5 = getChildDevice(getEventDeviceDni())
				if(!d5) {
					d5 = addChildDevice(namespace(), getEventDeviceName(), getEventDeviceDni(), [label: getEventDeviceName()])
					devsCrt = devsCrt + 1
					streamCnt = streamCnt + 1
					LogAction("Created Device: ${getEventDeviceName()} with (Id: ${getEventDeviceDni()})", "debug", true)
				} else {
					LogAction("Found Existing Device: ${getEventDeviceName()} with (Id: ${getEventDeviceDni()})", "debug", true)
				}
				devsInUse += getEventDeviceDni()
			}

			if(devsCrt > 0) {
				noCreates = false
				LogAction("Created ${devsCrt} Devices | (${tstats?.size()}) Thermostat(s), (${nVstats?.size() ?: 0}) Virtual Thermostat(s),(${nProtects?.size() ?: 0}) Protect(s), (${nCameras?.size() ?: 0}) Cameras(s), (${presCnt}) Presence Device, and (${streamCnt}) Event Stream Device", "debug", true)
				updTimestampMap("lastAnalyticUpdDt")
			}
		}

		if(uninst) {
			state.thermostats = [:]
			state.vThermostats = [:]
			state.protects = [:]
			state.cameras = [:]
			state.presDevice = false
			state.streamDevice = false
		}

		Boolean noDeleteErr = true
		def toDelete
		LogTrace("addRemoveDevices devicesInUse: ${devsInUse}")
		toDelete = getChildDevices().findAll { !devsInUse?.toString()?.contains(it?.deviceNetworkId) }

		if(toDelete?.size() > 0) {
			// log.debug "delete: $delete"
			// log.debug "devsInUse: $devsInUse"
			noDeletes = false
			noDeleteErr = false
			updTimestampMap("lastAnalyticUpdDt")
			LogAction("Removing ${toDelete.size()} devices: ${toDelete}", "debug", true)
			toDelete.each { deleteChildDevice(it.deviceNetworkId) }
			noDeleteErr = true
		}
		retVal = ((unist && noDeleteErr) || (!uninst && (noCreates && noDeletes))) ? true : false // it worked = no delete errors on uninstall; or no creates or deletes done
	} catch (ex) {
		if(ex instanceof hubitat.exception.ConflictException) {
			def msg = "Error: Can't Remove Device.  One or more of them are still in use by other Apps.  Please remove them and try again!"
			sendPush(msg)
			LogAction("addRemoveDevices Exception | $msg", "warn", true)
		} else {
			log.error "addRemoveDevices Exception: ${ex?.message}"
		}
		retVal = false
	}
	return retVal
}

Boolean addRemoveVthermostat(String tstatdni, tval, String myID) {
	String  odevId = tstatdni
	LogAction("addRemoveVthermostat() tstat: ${tstatdni}  devid: ${odevId}  tval: ${tval}  myID: ${myID} vThermostats: ${state.vThermostats} ", "trace", true)

	if(parent || !myID || tval == null) {
		LogAction("got called BADLY ${parent}  ${myID}  ${tval}", "warn", true)
		return false
	}
	String tstat = tstatdni
	def tStatPhys

	def d1 = getChildDevice(odevId)
	if(!d1) {
		LogAction("addRemoveVthermostat: Cannot find thermostat device child", "error", true)
		if(tval) { return false }  // if deleting (false), let it try to proceed
	} else {
		tstat = d1
		tStatPhys = tstat?.currentNestType == "physical" ? true : false
		if(!tStatPhys && tval) { LogAction("addRemoveVthermostat: Cannot create a virtual thermostat on a virtual thermostat device child", "error", true) }
	}

	String devId = "v${odevId}"

	// def migrate = migrationInProgress()

	// if(!migrate && state."vThermostat${devId}" && myID != state."vThermostatChildAppId${devId}") {
	if(state."vThermostat${devId}" && myID != state."vThermostatChildAppId${devId}") {
		LogAction("addRemoveVthermostat() not ours ${myID} ${state."vThermostat${devId}"} ${state."vThermostatChildAppId${devId}"}", "trace", true)
		//state."vThermostat${devId}" = false
		//state."vThermostatChildAppId${devId}" = null
		//state."vThermostatMirrorId${devId}" = null
		//state.vThermostats = null
		return false

	} else if(tval && state."vThermostat${devId}" && myID == state."vThermostatChildAppId${devId}") {
		LogAction("addRemoveVthermostat() already created ${myID} ${state."vThermostat${devId}"} ${state."vThermostatChildAppId${devId}"}", "trace", true)
		return true

	} else if(!tval && !state."vThermostat${devId}") {
		LogAction("addRemoveVthermostat() already removed ${myID} ${state."vThermostat${devId}"} ${state."vThermostatChildAppId${devId}"}", "trace", true)
		return true

	} else {
		state."vThermostat${devId}" = tval
		if(tval && !(String)state."vThermostatChildAppId${devId}") {
			LogAction("addRemoveVthermostat() marking for create virtual thermostat tracking ${tstat}", "trace", true)
			state."vThermostatChildAppId${devId}" = myID
			state."vThermostatMirrorId${devId}" = odevId
			Map vt = (Map)state.vThermostats ?: [:]
			vt[devId] = (String)tstat.label
			state.vThermostats = vt
			if(!settings.resetAllData) { runIn(120, "updated", [overwrite: true]) }  // create what is needed

		} else if(!tval && (String)state."vThermostatChildAppId${devId}") {
			LogAction("addRemoveVthermostat() marking for remove virtual thermostat tracking ${tstat}", "trace", true)
			state."vThermostatChildAppId${devId}" = sNULL
			state."vThermostatMirrorId${devId}" = sNULL

			state.remove("vThermostat${devId}" as String)
			state.remove("vThermostatChildAppId${devId}" as String)
			state.remove("vThermostatMirrorId${devId}" as String)
			state.remove("oldvstatData${devId}" as String)

			Map vt = state.vThermostats
			Map newmap = [:]
			def vtstat
			vtstat = vt.collect { dni ->
				//LogAction("vThermostats: ${state.vThermostats}  dni: ${dni}  dni.key: ${(String)dni.key}  dni.value: ${dni.value.toString()} devId: ${devId}", "debug", true)
				String ttkey = (String)dni.key
				if(ttkey == devId) {  /*log.trace "skipping $dni"*/ }
				else { newmap[ttkey] = dni.value }
				return true
			}
			vt = newmap
			state.vThermostats = vt
			if(!settings.resetAllData) { runIn(120, "updated", [overwrite: true]) }  // create what is needed
		} else {
			LogAction("addRemoveVthermostat() unexpected operation state ${myID} ${state."vThermostat${devId}"} ${state."vThermostatChildAppId${devId}"}", "warn", true)
			return false
		}
		return true
	}
}


Boolean getAccessToken() {
	if(!(String)state.access_token) {
		try {
			state.access_token = createAccessToken()
		}
		catch (ex) {
			String msg = "Error: OAuth is not Enabled for ${app.name}!.  Please click remove and Enable Oauth under the App Settings in the IDE".toString()
			sendPush(msg)
			log.error "getAccessToken Exception ${ex?.message}"
			LogAction("getAccessToken Exception | $msg", "warn", true)
			return false
		}
	}
	return true
}

void enableOauth() {
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

/*
void resetAppAccessToken() {
	LogAction("Resetting getAppDebugDesc Access Token....", "info", true)
	//restStreamHandler(true, "resetAppAccessToken()")
	//state.restStreamingOn = false
	revokeAccessToken()
	state.access_token = sNULL
	//resetPolling()
	if(getAccessToken()) {
		LogAction("Reset App Access Token... Successful", "info", true)
		settingUpdate("resetAppAccessToken", "false", "bool")
	}
	//startStopStream()
}
*/

/************************************************************************************************
 |					Below This line handle Hubitat >> Nest Token Authentication				|
 *************************************************************************************************/

Boolean nestDevAccountCheckOk() {
	if(getNestAuthToken()==sNULL && (clientId() == sNULL || clientSecret() == sNULL) ) { return false }
	else { return true }
}

static Map devClientData() {
	Integer clt = devCltNum() ?: 1
	Map m = [
		0: [id: "OWQxMzJlODMtMTFmYy00NWJlLTlhOGQtOTViN2E3Y2IwN2Ew", secret: "TERhSmU4dEFNdmRQR3lGUHQwSkpQMTY1eA=="],
		1: [id: "MzFhZWE0NmMtNDA0OC00YzJiLWI2YmUtY2FjN2ZlMzA1ZDRj", secret: "Rm1PNDY5R1hmZFNWam43UGhLbmpHV1psbQ=="],
		2: [id: "NjNlOWJlZmEtZGM2Mi00YjczLWFhZjQtZGNmMzgyNmRkNzA0", secret: "OGlxVDhYNDZ3YTJVWm5MMG9lM1RieU9hMA=="]
	]
	def id = m[clt]?.id?.decodeBase64()
	def secret = m[clt]?.secret?.decodeBase64()
	return [id: new String(id), secret: new String(secret)]
}

//These are the Nest OAUTH Methods to aquire the auth code and then Access Token.
String clientId() {
	if(settings.useMyClientId && settings.clientId) { return settings.clientId }
	String id = devClientData()?.id
	return id ?: sNULL//Developer ID
}

String clientSecret() {
	if(settings.useMyClientId && settings.clientSecret) { return settings.clientSecret }
	String sec=devClientData()?.secret
	return sec ?: sNULL//Developer Secret
}

String getNestAuthToken() { return (state.authData && state.authData?.token) ? (String)state.authData.token : sNULL }

String getOauthInitUrl() {
	Map oauthParams = [
		response_type: "code",
		client_id: clientId(),
		state: getOauthState(),
		redirect_uri: getCallbackUrl()
	]
//Logger("getOauthInitUrl:  https://home.nest.com/login/oauth2?${toQueryString(oauthParams)}", "error")
	return 'https://home.nest.com/login/oauth2?'+toQueryString(oauthParams)
}

def callback() {
	LogTrace("callback()")
	try {
		// LogTrace("callback()>> params: $params, params.code ${params.code}")
		def code = params.code
		// log.trace "Callback Code: $code"
		def oauthState = params.state
		// log.trace "Callback State: $oauthState"

		if(oauthState == getOauthState()) {
			Map tokenParams = [
					code: code.toString(),
					client_id: clientId(),
					client_secret: clientSecret(),
					grant_type: "authorization_code",
			]
			String tokenUrl = 'https://api.home.nest.com/oauth2/access_token?'+toQueryString(tokenParams)
//Logger("callback: https://api.home.nest.com/oauth2/access_token?${toQueryString(tokenParams)}", "error")
			httpPost(uri: tokenUrl) { resp ->
				Map authData = [:]
				authData.token = resp.data.access_token
				if(authData.token) {
					updTimestampMap("authTokenCreatedDt", getDtNow())
					authData.tokenExpires = resp.data.expires_in
					state.authData = authData
				}
			}
			if(state.authData?.token) {
				LogAction("Nest AuthToken Generated SUCCESSFULLY", "info", true)
				if((Boolean)state.isInstalled) {
					state.needStrPoll = true
					state.needDevPoll = true
					state.needMetaPoll = true
					state.needToFinalize = true
					checkRemapping()  // settings updates do not take immiediate effect, so we have to wait before using them
					state.pollBlocked = true
					state.pollBlockedReason = "Awaiting fixDevAS"
					runIn(4, "finishRemap", [overwrite: true])
				}
				success()
			} else {
				LogAction("Failure Generating Nest AuthToken", "error", true)
				fail()
			}
		} else { LogAction("callback() params.state != oauthInitState", "error", true) }
	} catch (ex) {
		log.error "Oauth Callback Exception: ${ex?.message}"
		revokeCleanState()
	}
}

void finishRemap() {
	LogTrace("finishRemap (${(Boolean)state.pollBlocked}) (${state.pollBlockedReason})")
	fixDevAS()
	state.pollBlocked = false
	state.pollBlockedReason = ""
	state.needToFinalize = false
	initialize()
}

static String getNestApiUrl()	{ return "https://developer-api.nest.com" }
String getStructure()	{ return (String)state.structures ?: sNULL }

static String getCallbackUrl(){ return "https://cloud.hubitat.com/api/nest" }
String getOauthState()	{ return "${getHubUID()}/apps/${app.id}/callback?access_token=${(String)state.access_token}".toString() }
String getAppEndpointUrl(subPath)	{ return "${getApiServerUrl()}/${getHubUID()}/apps/${app.id}${subPath ? "/${subPath}" : ""}?access_token=${(String)state.access_token}".toString() }
String getLocalEndpointUrl(subPath){ return "${getLocalApiServerUrl()}/apps/${app.id}${subPath ? "/${subPath}" : ""}?access_token=${(String)state.access_token}".toString() }

private static String sectionTitleStr(String title)	{ return '<h3>'+title+'</h3>' }
private static String inputTitleStr(String title)	{ return '<u>'+title+'</u>' }
private static String pageTitleStr(String title)	{ return '<h1>'+title+'</h1>' }
private static String paraTitleStr(String title)	{ return '<b>'+title+'</b>' }

static String imgTitle(String imgSrc, String titleStr, String color=sNULL, Integer imgWidth=30, Integer imgHeight=0) {
	String imgStyle = ""
	imgStyle += imgWidth>0 ? 'width: '+(String)(imgWidth.toString())+'px !important;':''
	imgStyle += imgHeight>0 ? imgWidth!=0 ? ' ':''+'height: '+(String)(imgHeight.toString())+'px !important;':''
	if(color!=sNULL){ return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""".toString() }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""".toString() }
}

// string table for titles
static String titles(String name, Object... args){
	Map page_titles=[
//		"page_main": "${lname} setup and management",
//		"page_add_new_cid_confirm": "Add new CID switch : %s",
//		"input_selected_devices": "Select device(s) (%s found)",
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
		"d_ttc": "<i>Tap to configure</i>",
		"d_ttm": "<i>Tap to modify</i>"
	]
	if(args)
		return String.format((String)element_descriptions[name],args)
	else
		return (String)element_descriptions[name]
}

@Field static final Map icon_namesFLD=[
	"i_lg": "login",
	"i_sw": "switch_on",
	"i_t": "temperature",
	"i_ns": "nest_structure",
	"i_th": "thermostat",
	"i_p": "protect",
	"i_c": "camera",
	"i_pr": "presence",
	"i_pu": "pushover",
	"i_s": "schedule",
	"i_d": "diagnostic",
	"i_g": "graph",
	"i_i": "issue",
	"i_r": "reset",
]

static String icons(String name, String napp="App"){
	String t0=icon_namesFLD."${name}"
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

//static String getAppImg(imgName, Boolean on = true)	{ return on ? "https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/$imgName" : "" }

//static String getDevImg(imgName, Boolean on = true)	{ return on ? "https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/Devices/$imgName" : "" }

void revokeNestToken() {
	if(getNestAuthToken()!=sNULL) {
		LogAction("revokeNestToken()", "info", true)
		restStreamHandler(true, "revokeNestToken()", false)
		state.restStreamingOn = false
		Map params = [
				uri: "https://api.home.nest.com",
				path: "/oauth2/access_tokens/${getNestAuthToken()}",
				contentType: 'application/json'
		]
		try {
			httpDelete(params) { resp ->
				if(resp?.status == 204) {
					LogAction("Nest Token revoked", "warn", true)
					revokeCleanState()
					//return //true
				}
			}
		}
		catch (ex) {
			if(ex?.message?.toString() == "Not Found") {
				revokeCleanState()
				//return //true
			} else {
				log.error "revokeNestToken Exception: ${ex?.message}"
				revokeCleanState()
				//return //false
			}
		}
	} else { revokeCleanState() }
}

void revokeCleanState() {
LogTrace("revokeCleanState")
	unschedule()
	atomicState.diagRunInOn = false
	state.access_token = sNULL
//	state.accessToken = null
	state.authData = null
	updTimestampMap("authTokenCreatedDt")
	//state.nestAuthTokenExpires = getDtNow()
	state.structData = null
	deviceDataFLD = null
	state.metaData = null
	updTimestampMap("lastStrDataUpd")
	updTimestampMap("lastDevDataUpd")
	updTimestampMap("lastMetaDataUpd")
	resetPolling()
	state.pollingOn = false
	state.streamPolling = false
	state.pollBlocked = true
	state.pollBlockedReason = "No Auth Token"
	atomicState.workQrunInActive = false
}

//HTML Connections Pages
def success() {
	String message = """
	<p>Your Hubitat Elevation is now connected to Nest!</p>
	<p>You will be redirected back to the Hubitat App to finish the rest of the setup in a couple seconds.</p>
	"""
	connectionStatus(message, true)
}

def fail() {
	String message = """
	<p>The connection could not be established!</p>
	<p>You will be redirected back to the Hubitat App to try the connection again.</p>
	"""
	connectionStatus(message, true)
}

def connectionStatus(String message, Boolean close = false) {
	String redirectHtml = close ? """<script>document.getElementsByTagName('html')[0].style.cursor = 'wait';setTimeout(function(){window.close()},2500);</script>""" : ""
	String html = """
		<!DOCTYPE html>
		<html>
		<head>
		<meta name="viewport" content="width=640">
		<title>Hubitat & Nest connection</title>
		<style type="text/css">
			@font-face {
				font-family: 'Swiss 721 W01 Thin';
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
				font-weight: normal;
				font-style: normal;
			}
			@font-face {
				font-family: 'Swiss 721 W01 Light';
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
				src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
						url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
				font-weight: normal;
				font-style: normal;
			}
			body { background: #2b3134;	}
			.container {
				width: 90%;
				padding: 4%;
				text-align: center;
				color: white;
			}
			img { vertical-align: middle; }
			p {
				font-size: 2.2em;
				font-family: 'Swiss 721 W01 Thin';
				text-align: center;
				padding: 0 40px;
				margin-bottom: 0;
			}
			span { font-family: 'Swiss 721 W01 Light'; }
		</style>
		</head>
		<body>
			<div class="container">
				<img src="https://community.hubitat.com/uploads/default/original/1X/f994d8c0dd92a7e88d22c5f84a633925f02d66e5.png" alt="Hubitat logo"  width="120"/>
				<img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
				<img src="${getAppImg("nest_icon128.png")}" alt="nest icon" width="120px"/>
				${message}
			</div>
		${redirectHtml}
		</body>
		</html>
		""".toString()
/* """ */
	render contentType: 'text/html', data: html
}

String toJson(Map m) {
	return new org.json.JSONObject(m).toString()
}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}".toString() }.sort().join("&")
}

/************************************************************************************************
 |									LOGGING AND Diagnostic										|
 *************************************************************************************************/
void LogTrace(GString msg, String logSrc=sNULL) {
	String value=(msg instanceof GString)? "$msg".toString():msg //get rid of GStrings
	LogTrace(value,logSrc)
}

void LogTrace(String msg, String logSrc=sNULL) {
	Boolean trOn = ((Boolean)settings.appDebug && (Boolean)settings.advAppDebug && !(Boolean)settings.enRemDiagLogging) ? true : false
	if(trOn) {
		Boolean logOn = ((Boolean)settings.enRemDiagLogging && (Boolean)state.enRemDiagLogging) ? true : false
		//def theLogSrc = (logSrc == null) ? (parent ? "Automation" : "Manager") : logSrc
		Logger(msg, "trace", logSrc, logOn)
	}
}

void LogAction(GString msg, String type="debug", Boolean showAlways=false, String logSrc=sNULL) {
	String value=(msg instanceof GString)? "$msg".toString():msg //get rid of GStrings
	LogAction(value,type,showAlways,logSrc)
}

void LogAction(String msg, String type="debug", Boolean showAlways=false, String logSrc=sNULL) {
	Boolean isDbg = ((Boolean)settings.appDebug /* && !(Boolean)enRemDiagLogging */) ? true : false
	//def theLogSrc = (logSrc == null) ? (parent ? "Automation" : "Manager") : logSrc
	if(showAlways || (isDbg && !showAlways)) { Logger(msg, type, logSrc) }

//	if(showAlways || isDbg) { Logger(msg, type) }
}

static String tokenStrScrubber(String str) {
	String regex1 = /(Bearer c.{1}\w+)/
	String regex2 = /(auth=c.{1}\w+)/
	String newStr = str.replaceAll(regex1, "Bearer 'token code redacted'")
	newStr = newStr.replaceAll(regex2, "auth='token code redacted'")
	//log.debug "newStr: $newStr"
	return newStr
}

void Logger(String msg, String type, String logSrc=sNULL, Boolean noSTlogger=false) {
	String labelstr = ""
	Boolean logOut = true
	if((Boolean)settings.dbgAppndName) { labelstr = app.label+' | ' }
	if(msg && type) {
		String themsg = tokenStrScrubber(labelstr+msg)
		if((Boolean)state.enRemDiagLogging && (Boolean)settings.enRemDiagLogging && (Boolean)state.remDiagAppAvailable) {
			String theLogSrc = (logSrc == sNULL) ? (parent ? "Automation" : "Manager") : logSrc
			if(saveLogtoRemDiagStore(themsg, type, theLogSrc) == true) {
				logOut = false
			}
		}
		if(logOut == true) {
		switch(type) {
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
	else { log.error "${labelstr}Logger Error - type: ${type} | msg: ${msg}" }
}

String getDiagLogTimeRemaining() {
	return sec2PrettyTime((3600*48) - Math.abs((getRemDiagActSec() ?: 0)))
}

String sec2PrettyTime(Integer timeSec) {
	Integer years = Math.floor(timeSec / 31536000); timeSec -= years * 31536000
	Integer months = Math.floor(timeSec / 31536000); timeSec -= months * 2592000
	Integer days = Math.floor(timeSec / 86400); timeSec -= days * 86400
	Integer hours = Math.floor(timeSec / 3600); timeSec -= hours * 3600
	Integer minutes = Math.floor(timeSec / 60); timeSec -= minutes * 60
	Integer seconds = Integer.parseInt((timeSec % 60) as String, 10)
	Map dt = [y: years, mn: months, d: days, h: hours, m: minutes, s: seconds]
	String dtStr = ""
	// dtStr += dt?.y ? "${dt?.y}yr${dt?.y>1?"s":""}, " : ""
	// dtStr += dt?.mn ? "${dt?.mn}mon${dt?.mn>1?"s":""}, " : ""
	// dtStr += dt?.d ? "${dt?.d}day${dt?.d>1?"s":""}, " : ""
	// dtStr += dt?.h ? "${dt?.h}hr${dt?.h>1?"s":""} " : ""
	// dtStr += dt?.m ? "${dt?.m}min${dt?.m>1?"s":""} " : ""
	// dtStr += dt?.s ? "${dt?.s}sec" : ""
	dtStr += dt?.d ? "${dt?.d}d " : ""
	dtStr += dt?.h ? "${dt?.h}h " : ""
	dtStr += dt?.m ? "${dt?.m}m " : ""
	dtStr += dt?.s ? "${dt?.s}s" : ""
	return dtStr
}

Boolean saveLogtoRemDiagStore(String msg, String type, String logSrcType=sNULL, Boolean frc=false) {
	Boolean retVal = false
	// log.trace "saveLogtoRemDiagStore($msg, $type, $logSrcType)"
	if((Boolean)state.enRemDiagLogging && (Boolean)settings.enRemDiagLogging) {
/*
		def turnOff = false
		def reasonStr = ""
		if(frc == false) {
			if(getRemDiagActSec() > (3600 * 48)) {
				turnOff = true
				reasonStr += "was active for last 48 hours "
			}
		}
		if(turnOff) {
			saveLogtoRemDiagStore("Diagnostics disabled due to ${reasonStr}", "info", "Manager", true)
			diagLogProcChange(false)
			log.info "Remote Diagnostics disabled ${reasonStr}"
		} else {
*/
		if(getStateSizePerc() >= 68) {
			log.warn "saveLogtoRemDiagStore: remoteDiag log storage suspended state size is ${getStateSizePerc()}%"
		} else {
			if(msg) {
				Long dt = new Date().getTime()
				Map item = ["dt":dt, "type":type, "src":(logSrcType ?: "Not Set"), "msg":msg]
				List t0 = (List)atomicState.remDiagLogDataStore
				List data = t0 ?: []
				data << item
				atomicState.remDiagLogDataStore = data
				retVal = true
			}
		}

		if(frc) {
			List t0 = (List)atomicState.remDiagLogDataStore
			List data = t0 ?: []
			Integer t1 = (Integer)data.size()
			if(t1) {
				diagLogChecks(frc)
			}
		} else {
			if(!(Boolean)atomicState.diagRunInOn) {
				atomicState.diagRunInOn = true
				runIn(10, "diagLogChecks", [overwrite: true])
			}
		}
/*
			List data = (List)atomicState.remDiagLogDataStore ?: []
			def t0 = data?.size()
			if(t0 && (t0 > 30 || frc || getLastRemDiagSentSec() > 120 || getStateSizePerc() >= 65)) {
				def remDiagApp = getRemDiagApp()
				if(remDiagApp) {
					remDiagApp.savetoRemDiagChild(data)
					updTimestampMap("remDiagDataSentDt", getDtNow())
				} else {
					log.warn "Remote Diagnostics Child app not found"
					if(getRemDiagActSec() > 20) {   // avoid race that child did not start yet
						diagLogProcChange(false)
					}
					retVal = false
				}
				atomicState.remDiagLogDataStore = []
			}
		}
*/
	}
	return retVal
}

void diagLogChecks(Boolean frc=false) {
	atomicState.diagRunInOn = false
	if((Boolean)state.enRemDiagLogging && (Boolean)settings.enRemDiagLogging) {
		String reasonStr = ""
		if(!frc && getRemDiagActSec() > (3600 * 48)) {
			reasonStr += "was active for last 48 hours "
			Boolean a=saveLogtoRemDiagStore("Diagnostics disabled due to ${reasonStr}", "info", "Manager", true)
			diagLogProcChange(false)
			log.info "Remote Diagnostics disabled ${reasonStr}"
			return
		}
		List t1 = (List)atomicState.remDiagLogDataStore
		List data = t1 ?: []
		Integer t0 = (Integer)data.size()
		if(t0 && (t0 > 30 || frc || getLastRemDiagSentSec() > 120 || getStateSizePerc() >= 65)) {
			def remDiagApp = getRemDiagApp()
			if(remDiagApp) {
				remDiagApp?.savetoRemDiagChild(data)
				atomicState.remDiagLogDataStore = []
				updTimestampMap("remDiagDataSentDt", getDtNow())
			} else {
				log.warn "Remote Diagnostics Child app not found"
				if(getRemDiagActSec() > 20) {   // avoid race that child did not start yet
					diagLogProcChange(false)
				}
			}
		}
	}
}

void settingUpdate(String name, String value, String type=sNULL) {
	//LogAction("settingUpdate($name, $value, $type)...", "trace", false)
	if(name){
		if(type!=sNULL) app.updateSetting(name, [type: type, value: value])
		else app.updateSetting(name, value)
	}
}

void settingRemove(String name) {
	//LogAction("settingRemove($name)...", "trace", false)
	if(name) app.clearSetting(name)
}
/*
void stateUpdate(String key, value) {
	if(key) state."${key}" = value
	else LogAction("stateUpdate: null key $key $value", "error", true)
}
*/
//Things that need to clear up on updates go here
void stateCleanup() {
	// LogAction("stateCleanup", "trace", true)
	List<String> data = [ "deviceData", "cmdIsProc", "apiIssuesList", "cmdQlist", "nestRedirectUrl" /*, "timestampDtMap" , "accessToken" */ ]
	["lastCmdSent", "recentSendCmd", "cmdQ", "remSenLock", "oldTstat", "oldvstat", "oldCamData", "oldProt", "oldPres" ]?.each { String oi->
		state.each { if( (Boolean)((String)it.key).startsWith(oi)) { data.push((String)it.key) } }
	}
	data.each { String item ->
		state.remove(item)
	}
	updTimestampMap("lastApiIssueMsgDt")
	atomicState.workQrunInActive = false
	state.forceChildUpd = true
	List<String> sdata = ["updChildOnNewOnly", 'debugAppendAppName' ]
	sdata.each { String item ->
		if(settings."${item}" != null) {
			settingUpdate(item, "")	// clear settings
		}
	}
}

/******************************************************************************
 *								STATIC METHODS								*
 *******************************************************************************/
static String getThermostatChildName()	{ return "Nest Thermostat" }

static String getProtectChildName()	{ return "Nest Protect" }

static String getPresenceChildName()	{ return "Nest Presence" }

static String getCameraChildName()	{ return "Nest Camera" }

static String getEventDeviceName()	{ return "Nest Eventstream" }

static String getEventDeviceDni()	{ return "nest-eventstream01" }

/*
private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }
private String convertHexToIP(hex) { [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".") }
*/

//Returns app State Info
Integer getStateSize() {
	String resultJson = new groovy.json.JsonOutput().toJson(state)
	return resultJson.length()
}
Integer getStateSizePerc()  { return (Integer) ((stateSize / 100000)*100).toDouble().round(0) } //

String debugStatus() { return !(Boolean)settings.appDebug ? "Off" : "On" }
Boolean isAppDebug() { return !(Boolean)settings.appDebug ? false : true }

static String getObjType(obj) {
	if(obj instanceof String) {return "String"}
	else if(obj instanceof GString) {return "GString"}
	else if(obj instanceof Map) {return "Map"}
	else if(obj instanceof List) {return "List"}
	else if(obj instanceof ArrayList) {return "ArrayList"}
	else if(obj instanceof Integer) {return "Integer"}
	else if(obj instanceof BigInteger) {return "BigInteger"}
	else if(obj instanceof Long) {return "Long"}
	else if(obj instanceof Boolean) {return "Boolean"}
	else if(obj instanceof BigDecimal) {return "BigDecimal"}
	else if(obj instanceof Float) {return "Float"}
	else if(obj instanceof Byte) {return "Byte"}
	else { return "unknown"}
}

def getTimeZone() {
	def tz = null
	if(location?.timeZone) { tz = location?.timeZone }
	else { tz = getNestTimeZone() ? TimeZone.getTimeZone(getNestTimeZone()) : null }
	if(!tz) { LogAction("getTimeZone: Hub or Nest TimeZone not found", "warn", true) }
	return tz
}

String formatDt(Date dt) {
	SimpleDateFormat tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("Hubitat TimeZone is not set; Please verify your Zip code is set under Hub Settings", "warn", true)
	}
	return tf.format(dt)
}

private Integer getTimeSeconds(String timeKey, Integer defVal, String meth) {
	String t0 = getTimestampVal(timeKey)
	return !t0 ? defVal : GetTimeDiffSeconds(t0, sNULL, meth).toInteger()
}

String getTimestampVal(String keyName) {
	Map tsData = state.timestampDtMap
	if(keyName && tsData && tsData[keyName]) { return (String)tsData[keyName] }
	return sNULL
}

void updTimestampMap(String keyName, String dt=sNULL) {
	Map data = state.timestampDtMap ?: [:]
	if(keyName) { data[keyName] = dt }
	state.timestampDtMap = data
}

Long GetTimeDiffSeconds(String strtDate, String stpDate=sNULL, String methName=sNULL) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		String stopVal = stpDate!=sNULL ? stpDate : getDtNow()
		Long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Long diff = (stop - start) / 1000L //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

static String strCapitalize(String str) {
	return str ? str.capitalize() : sNULL
}

def getSettingVal(String var) {
	if(var == sNULL) { return settings }
	return settings[var] ?: null
}

def getStateVal(String var) {
	return state[var] ?: null
}

// Calls by Automation children
// parent only method

Boolean remSenLock(val, String myId) {
	Boolean res = false
	String k = "remSenLock${val}".toString()
	if(val && myId) {
		String lval = (String)state."${k}"
		if(!lval) {
			state."${k}" = myId
			res = true
		} else if(lval == myId) { res = true }
	}
	return res
}

Boolean remSenUnlock(val, String myId) {
	Boolean res = false
	if(val && myId) {
		String k = "remSenLock${val}".toString()
		String lval = (String)state."${k}"
		if(lval) {
			if(lval == myId) {
				state."${k}" = null
				state.remove("${k}" as String)
				res = true
			}
		} else { res = true }
	}
	return res
}

Boolean automationNestModeEnabled(Boolean val=null) {
	LogTrace("NestModeEnabled: $val")
	return getSetVal("automationNestModeEnabled", val)

/*
	if(val == null) {
		return state.automationNestModeEnabled ?: false
	} else {
		state.automationNestModeEnabled = val.toBoolean()
	}
	return state.automationNestModeEnabled ?: false
*/
}

Boolean setNModeActive(Boolean val=null) {
	LogTrace("setNModeActive: $val")
	String  myKey = "automationNestModeEcoActive"
	Boolean retVal
	if(!automationNestModeEnabled(null)) {
		retVal = getSetVal(myKey, false)
/*
	if(automationNestModeEnabled(null)) {
		return getSetVal(myKey, val)
		if(val == null) {
			return state.automationNestModeEcoActive ?: false
		} else {
			state.automationNestModeEcoActive = val.toBoolean()
		}

	} else { getSetVal(myKey, false) }
	//return state.automationNestModeEcoActive ?: false
*/
	} else { retVal = getSetVal(myKey, val) }
	return retVal
}

Boolean getSetVal(String k, Boolean val=null) {
	if(val == null) {
		return state."${k}" ?: false
	} else {
		state."${k}" = val.toBoolean()
	}
	return state."${k}" ?: false
}

def getDevice(String dni) {
	def d = getChildDevice(dni)
	if(d) { return d }
	return null
}

def getDevices() {
	def d = getChildDevices()
	if(d) { return d }
	return null
}

def getTheChildren() {
	def d = getChildApps()
	if(d) { return d }
	return null

}

static String appLabel()	{ return "NST Manager" }
static String gitRepo()		{ return "tonesto7/nest-manager"}
static String gitBranch()	{ return "master" }
static String gitPath()		{ return gitRepo()+'/'+gitBranch() }
