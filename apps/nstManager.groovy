/**
 *  NST Manager
 *	Copyright (C) 2017, 2018, 2019 Anthony Santilli
 *	Author: Anthony Santilli (@tonesto7) Eric Schott (@nh.schottfam)
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

String appVer() { "2.0.6" }
String namespace()  { "tonesto7" }
int devCltNum() { 1 }
boolean restEnabled(){ true } // Enables the Rest Stream Device
int DevPoll() { 60 } // 1 minute poll time (when rest is not active)
int StrPoll() { 120 } // 2 minute poll time (when rest is not active)
int MetaPoll() { 3600*4 } // 4 hrs poll time (when rest is not active)
int refreshWait() { 10 } // Restricts Manual Refreshes to every every 10 seconds
int tempChgWaitVal() { 3 } // This is the wait time after manually changing temp before sending the command.  It allows successive changes and avoids exceeding nest command limits

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

/******************************************************************************
 |					Application Pages			|
 *******************************************************************************/
def appInfoSect()	{
	section() {
		String str = """
		<div class="appInfoSect" style="width: 300px; height: 70px; display: inline-table;">
			<ul style=" margin: 0 auto; padding: 0; list-style-type: none;">
			<img style="float: left; padding: 10px;" src="https://pbs.twimg.com/profile_images/519883786020847617/TqhjjrE__400x400.png" width="60px"/>
			<li style="padding-top: 2px;"><b><i>${app?.name}</i></b></li>
			<li><small style="color: black !important;">Version: ${appVer()}</small></li>
			</ul>
		</div>
		<script>\$('.appInfoSect').parent().css("cssText", "font-family: Arial !important; white-space: inherit !important;")</script>
		"""
		paragraph "${str}"
	}
}

def authPage() {
	//LogTrace("authPage()")
	String description
	boolean oauthTokenProvided = false

	if(getNestAuthToken()) {
		description = "<i>You are connected.</i>"
		oauthTokenProvided = true
 		return mainPage()
	} else {
		description = "<i>Tap to enter Nest Login Credentials</i>"
		if(!state?.access_token) { getAccessToken() }
		if(!state?.access_token) { enableOauth(); getAccessToken() }
		boolean ok4Main = (state?.access_token && nestDevAccountCheckOk())
		return dynamicPage(name: "authPage", title: "", nextPage: ok4Main ? "mainPage" : "", install: false, uninstall: false) {
			if(!ok4Main) {
				section () {
					String title = ""
					String desc = ""
					if(!state?.access_token) {
						title = "OAuth Error"
						desc = "OAuth is not Enabled for ${app?.name} application.  Please click remove and review the installation directions again"
					} else if(!nestDevAccountCheckOk()) {
						title = "Nest Developer Data Missing"
						desc = "Client ID and Secret\nAre both missing!\n\nThe built-in Client ID and Secret can no longer be provided.\n\nPlease visit the Wiki at the link below to resolve the issue."
					} else {
						desc = "Application Status has not received any messages to display"
					}
					LogAction("Status Message: $desc", "warn", true)
					paragraph "$desc", required: true, state: null
				}
			}
			section () {
				input(name: "useMyClientId", type: "bool", title: imgTitle(getAppImg("login_icon.png"), inputTitleStr("Enter your own ClientId?")), required: false, defaultValue: false, submitOnChange: true)
				if(useMyClientId) {
					input("clientId", "text", title: imgTitle(getAppImg("login_icon.png"), inputTitleStr("Nest ClientId")), defaultValue: "", required: true, submitOnChange: true, image: getAppImg("login_icon.png"))
					input("clientSecret", "text", title: imgTitle(getAppImg("login_icon.png"), inputTitleStr("Nest Client Secret")), defaultValue: "", required: true, submitOnChange: true, image: getAppImg("login_icon.png"))
				} else {
//					settingUpdate("clientId", "")
//					settingUpdate("clientSecret", "")
				}
			}
			if(ok4Main && !getNestAuthToken()) {
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
	boolean isInstalled = state?.isInstalled
	boolean setupComplete = (isInstalled == true)
	return dynamicPage(name: "mainPage", title: "", nextPage: (!setupComplete ? "reviewSetupPage" : null), install: true, uninstall: isInstalled) {
		appInfoSect()
		String ttm_str = "Tap to modify"
		String ttc_str = "Tap to configure"
		if(isInstalled) {
			if(settings?.structures && !state?.structures) { state?.structures = settings?.structures }
			section(sectionTitleStr("Nest Location Mode:")) {
				String pres = getLocationPresence()
				String color = (pres == "away") ? "orange" : (pres == "home" ? "#00c9ff" : null)
				paragraph imgTitle(getAppImg("home_icon.png"), "${strCapitalize(pres ?: "Not Available Yet!")}", color), state: "complete"
			}
			section(sectionTitleStr("Devices & Location:")) {
				String t1 = getDevicesDesc(false)
				String devDesc = t1 ? "${t1}\n\n<i>${ttm_str}</i>" : "<i>${ttc_str}</i>"
				href "deviceSelectPage", title: inputTitleStr("Manage/View Devices"), description: devDesc, state: "complete"
			}
		}
		if(!isInstalled) { devicesPage() }
		if(isInstalled) {
			if(state?.structures && (state?.thermostats || state?.protects || state?.cameras)) {
				String t1 = getInstAutoTypesDesc()
				String autoDesc = t1 ? "${t1}\n\n<i>${ttm_str}</i>" : "<i>${ttc_str}</i>"
				section("Manage Automations:") {
					href "automationsPage", title: imgTitle(getAppImg("nst_automations_5.png"), inputTitleStr("Automations")), description: autoDesc, state: (t1 ? "complete" : null)
				}

			}
			section("Notifications Options:") {
				String t1 = getAppNotifConfDesc()
				href "notifPrefPage", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Notifications")), description: (t1 ? "${t1}<i>\n\n${ttm_str}</i>" : "<i>${ttc_str}</i>"), state: (t1 ? "complete" : null)
			}
			section(sectionTitleStr("Nest Authentication:")) {
				String t1 = getNestAuthToken() ? "Nest Authorized\n<b>Last API Connection:</b>\n• ${getTimestampVal("lastDevDataUpd")}" : ""
				String authDesc = t1 ? "${t1}\n\n<i>${ttm_str}</i>" : "<i>${ttc_str}</i>"
				href "nestLoginPrefPage", title: imgTitle(getAppImg("login_icon.png"), inputTitleStr("Manage Login")), description: authDesc, state: (t1 ? "complete" : null)
			}
			section(sectionTitleStr("App Logging:")) {
				String t1 = getAppDebugDesc()
				href "debugPrefPage", title: imgTitle(getAppImg("log.png"), inputTitleStr("Configure Logging")), description: (t1 ? "${t1 ?: ""}\n\n<i>${ttm_str}</i>" : "<i>${ttc_str}</i>"), state: (t1) ? "complete" : null
			}
		}
	}
}

def deviceSelectPage() {
	boolean isInstalled = state?.isInstalled
	return dynamicPage(name: "deviceSelectPage", title: pageTitleStr("Device Selection"), nextPage: (!isInstalled ? "mainPage" : null), install: true, uninstall: false) {
		devicesPage()
	}
}

def devicesPage() {
	def structs = getNestStructures()
	boolean isInstalled = state?.isInstalled
	String structDesc = !structs?.size() ? "No Locations Found" : "Found (${structs?.size()}) Locations"
	if (state?.thermostats || state?.protects || state?.cameras || state?.presDevice ) {  // if devices are configured, you cannot change the structure until they are removed
		section(sectionTitleStr("Nest Location:")) {
			paragraph imgTitle(getAppImg("nest_structure_icon.png"), "${inputTitleStr("Name:")} ${structs[state?.structures]}${(structs.size() > 1) ? "\n(Remove All Devices to Change!)" : ""}")
		}
	} else {
		section(sectionTitleStr("Select Location:")) {
			input(name: "structures", title: imgTitle(getAppImg("nest_structure_icon.png"), inputTitleStr("Available Locations")), type: "enum", required: true, multiple: false, submitOnChange: true, options: structs)
		}
	}
	if (settings?.structures) {
		state.structures = settings?.structures
		String newStrucName = structs && structs?."${state?.structures}" ? "${structs[state?.structures]}" : null
		state.structureName = newStrucName ?: state?.structureName

		def stats = getNestThermostats()
		def coSmokes = getNestProtects()
		def cams = getNestCameras()

		section(sectionTitleStr("Select Devices:")) {
			if(!stats?.size() && !coSmokes.size() && !cams?.size()) { paragraph "<h2>No Devices were found</h2>" }
			if(stats?.size() > 0) {
				input(name: "thermostats", title: imgTitle(getAppImg("thermostat_icon.png"), """<u>Nest Thermostats</u><small style="color: blue !important;"> (${stats?.size()} found)</small>"""), type: "enum", required: false, multiple: true, submitOnChange: true, options:stats)
			}
			state.thermostats = settings?.thermostats ? statState(settings?.thermostats) : null
			if(coSmokes.size() > 0) {
				input(name: "protects", title: imgTitle(getAppImg("protect_icon.png"), """<u>Nest Protects</u><small style="color: blue !important;"> (${coSmokes?.size()} found)</small>"""), type: "enum", required: false, multiple: true, submitOnChange: true, options: coSmokes)
			}
			state.protects = settings?.protects ? coState(settings?.protects) : null
			if(cams.size() > 0) {
				input(name: "cameras", title: imgTitle(getAppImg("camera_icon.png"), """<u>Nest Cameras</u><small style="color: blue !important;"> (${cams?.size()} found)</small>"""), type: "enum", required: false, multiple: true, submitOnChange: true, options: cams)
			}
			state.cameras = settings?.cameras ? camState(settings?.cameras) : null
			input(name: "presDevice", title: imgTitle(getAppImg("presence_icon.png"), inputTitleStr("Add Presence Device?")), type: "bool", defaultValue: false, required: false, submitOnChange: true)
			state.presDevice = settings?.presDevice ?: null

			input "weatherDevice", "capability.relativeHumidityMeasurement", title: imgTitle(getAppImg("temperature_icon.png"), inputTitleStr("External Weather Devices?")), required: false, multiple: false, submitOnChange: true
		}

		boolean devSelected = (state?.structures && (state?.thermostats || state?.protects || state?.cameras || state?.presDevice))
		if(isInstalled && devSelected) {
			section("<h3>Customize Device Names:</h3>") {
				String descStr
				if(state?.devNameOverride) {
					if(state?.custLabelUsed) {
						descStr = "• Custom Labels Are Active"
					}
					if(state?.useAltNames) {
						descStr = "• Using Location Name as Prefix is Active"
					}
				}
				String devDesc = (descStr) ? "\n ${descStr}\n\n<i>Tap to modify</i>" : "<i>Tap to configure</i>"
				href "devNamePage", title: imgTitle(getAppImg("device_name_icon.png"), inputTitleStr("Device Names")), description: devDesc, state:(!state?.devNameOverride || (state.devNameOverride && (state?.custLabelUsed || state?.useAltNames))) ? "complete" : ""
			}
		}
	}
}

def reviewSetupPage() {
	return dynamicPage(name: "reviewSetupPage", title: "", install: true, uninstall: state?.isInstalled) {
		section(sectionTitleStr("Device Setup Summary:")) {
			String t0 = getDevicesDesc()
			String str = t0 ?: ""
			paragraph title: (!state?.isInstalled ? "Devices Pending Install:" : "Installed Devices:"), "${str}"
			paragraph '<p style="color: blue;">Tap <b>Done</b> to complete the install and create the devices selected</p>', state: "complete"
		}
	}
}

def devNamePage() {
	String pagelbl = state?.isInstalled ? "Device Labels" : "Custom Device Labels"
	dynamicPage(name: "devNamePage", title: pageLbl, nextPage: "", install: false) {
		if(settings?.devNameOverride == null || state?.devNameOverride == null) {
			state.devNameOverride = true;
			settingUpdate("devNameOverride","true","bool")
		}
		boolean overrideName = (state?.devNameOverride == true)
		boolean altName = (state?.useAltNames == true)
		boolean custName = (state?.custLabelUsed == true)
		section(sectionTitleStr("Device Name Settings")) {
			input (name: "devNameOverride", type: "bool", title: inputTitleStr("App Overwrites Device Names?"), required: false, defaultValue: overrideName, submitOnChange: true )
			if(devNameOverride && !useCustDevNames) {
				input (name: "useAltNames", type: "bool", title: inputTitleStr("Use Location Name as Prefix?"), required: false, defaultValue: altName, submitOnChange: true, image: "" )
			}
			if(devNameOverride && !useAltNames) {
				input (name: "useCustDevNames", type: "bool", title: inputTitleStr("Assign Custom Names?"), required: false, defaultValue: custName, submitOnChange: true, image: "" )
			}

			state.devNameOverride = settings?.devNameOverride ? true : false
			if(state.devNameOverride) {
				state.useAltNames = settings?.useAltNames ? true : false
				state.custLabelUsed = settings?.useCustDevNames ? true : false
			} else {
				state.useAltNames = false
				state.custLabelUsed = false
			}
/*
			if(state?.custLabelUsed) {
				paragraph "Custom Labels Are Active", state: "complete"
			}
			if(state?.useAltNames) {
				paragraph "Using Location Name as Prefix is Active", state: "complete"
			}
*/
			//paragraph "Current Device Handler Names", image: ""
		}

		boolean found = false
		if(state?.thermostats) {
			section (sectionTitleStr("Thermostat Device(s):")) {
				state.thermostats?.each { t ->
					found = true
					def d = getChildDevice(getNestTstatDni(t))
					deviceNameFunc(d, getNestTstatLabel(t.value, t.key), "tstat_${t?.key}_lbl", "thermostat")
				}
				state?.vThermostats?.each { t ->
					found = true
					def d = getChildDevice(getNestvStatDni(t))
					deviceNameFunc(d, getNestVstatLabel(t.value, t.key), "vtstat_${t?.key}_lbl", "thermostat")
				}
			}
		}
		if(state?.protects) {
			section (sectionTitleStr("Protect Device Names:")) {
				state?.protects?.each { p ->
					found = true
					def d = getChildDevice(getNestProtDni(p))
					deviceNameFunc(d, getNestProtLabel(p.value, p.key), "prot_${p?.key}_lbl", "protect")
				}
			}
		}
		if(state?.cameras) {
			section (sectionTitleStr("Camera Device Names:")) {
				state?.cameras?.each { c ->
					found = true
					def d = getChildDevice(getNestCamDni(c))
					deviceNameFunc(d, getNestCamLabel(c.value, c.key), "cam_${c?.key}_lbl", "camera")
				}
			}
		}
		if(state?.presDevice) {
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
		dstr += "<u>Found:</u> ${dev.displayName}"
		if(dev.displayName != label) {
			String str1 = "\n\n<b>Name is not set to default.\nDefault name is:</b>"
			dstr += "$str1\n${label}"
		}
	} else {
		dstr += "<b>New Name:</b>\n${label}"
	}
	String dtyp =  state?.custLabelUsed ? "blank" : devType
	paragraph imgTitle(getAppImg("${dtyp}_icon.png"), "${dstr}", color), state: "complete"
	//paragraph "${dstr}", state: "complete", image: (state?.custLabelUsed) ? " " : getAppImg("${devType}_icon.png")
	if(state?.custLabelUsed) {
		input "${inputStr}", "text", title: imgTitle(getAppImg("${devType}_icon.png"), inputTitleStr("Custom name for ${label}")), defaultValue: label, submitOnChange: true
	}
 }

String getAppNotifConfDesc() {
	String str = ""
	if(settings?.phone || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
		str += (settings?.pushoverEnabled) ? "${str != "" ? "\n" : ""}Pushover: (Enabled)" : ""
		str += (settings?.phone) ? "${str != "" ? "\n" : ""}Sending via: (SMS)" : ""

		if(str != "") {
			String t0 = ""
			t0 += settings?.appApiIssuesMsg != false ? "\n • API CMD Failures" : ""
			t0 += settings?.locPresChangeMsg != false ? "\n • Nest Home/Away Status Changes" : ""
			t0 += settings?.camStreamNotifMsg != false ? "\n • Camera Stream Alerts" : ""
			t0 += settings?.automationNotifMsg != false ? "\n • Automation Notifications" : ""
			if(t0 != "") {
				str += "\n\nAlerts:"
				str += "${t0}"
			}
		}
	}
	return str != "" ? str : (String)null
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
		section("Enable Text Messaging:") {
			input "phone", "phone", title: imgTitle(getAppImg("notification_icon2.png"), inputTitleStr("Send SMS to Number\n(Optional)")), required: false, submitOnChange: true
		}
		section("Enable Notification Devices:") {
			input "pushoverEnabled", "bool", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
			if(settings?.pushoverEnabled == true) {
				input "pushoverDevices", "capability.notification", title: imgTitle(getAppImg("pushover_icon.png"), inputTitleStr("Notification Device")), required: false, submitOnChange: true
			}
		}
		if(settings?.phone || (settings?.pushoverEnabled && settings?.pushoverDevices)) {
/*
			section("Notification Restrictions:") {
				def t1 = getNotifSchedDesc()
				href "setNotificationTimePage", title: "Notification Restrictions", description: (t1 ?: "Tap to configure"), state: (t1 ? "complete" : null), image: getAppImg("restriction_icon.png")
			}
*/
			if( (settings?.pushoverEnabled && settings?.pushoverDevices) && !state?.pushTested) {
				if(sendMsg("Info", "Push Notification Test Successful. Notifications Enabled for ${app.label}", 0)) {
					state.pushTested = true
				}
			}
			section("Alerts:") {
				paragraph "Receive notifications when there are issues with the Nest API", state: "complete"
				input "appApiIssuesMsg", "bool", title: imgTitle(getAppImg("issue_icon.png"), inputTitleStr("Notify on API Issues?")), defaultValue: true, submitOnChange: true
				paragraph "Get notified when the Location changes from Home/Away", state: "complete"
				input "locPresChangeMsg",  "bool", title: imgTitle(getAppImg("presence_icon.png"), inputTitleStr("Notify on Nest Home/Away changes?")), defaultValue: true, submitOnChange: true
				if(settings?.cameras) {
					paragraph "Get notified on Camera streaming changes", state: "complete"
					input "camStreamNotifMsg", "bool", title: imgTitle(getAppImg("camera_icon.png"), inputTitleStr("Send Cam Streaming Alerts?")), required: false, defaultValue: true, submitOnChange: true
				}
				paragraph "Automation Notification Messages", state: "complete"
				input "automationNotifMsg",  "bool", title: imgTitle(getAppImg("issue_icon.png"), inputTitleStr("Automation Notifications?")), defaultValue: true, submitOnChange: true
			}
		}
	}
}

boolean sendMsg(String msgType, String msg, int lvl, pushoverDev=null, sms=null) {
	String newMsg = "${msgType}: ${msg}"
	LogTrace("sendMsg $lvl $newMsg")
	boolean retVal = false
	if(lvl == 1 && settings?.appApiIssuesMsg == false) { return }
	if(lvl == 2 && settings?.locPresChangeMsg == false) { return }
	if(lvl == 3 && settings?.camStreamNotifMsg == false) { return }
	if((lvl == 4 || lvl == 5) && settings?.automationNotifMsg == false) { return }
	def notifDev = pushoverDev ?: settings?.pushoverDevices
	if(notifDev && settings?.pushoverEnabled) {
		retVal = true
		notifDev*.deviceNotification(newMsg)
	}
	String thephone = sms ? sms.toString() : settings?.phone ? settings?.phone?.toString() : ""
	if(thephone) {
		retVal = true
		String t0 = newMsg.take(140)
		sendSms(thephone, t0)
	}
	return retVal
}

def debugPrefPage() {
	dynamicPage(name: "debugPrefPage", install: false) {
		section (sectionTitleStr("Application Logs")) {
			input ("dbgAppndName", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Show App/Device Name on all Log Entries?")), required: false, defaultValue: false, submitOnChange: true)
			input ("appDebug", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Show ${app?.name} Logs in the IDE?")), required: false, defaultValue: false, submitOnChange: true)
			if(settings?.appDebug) {
				input ("advAppDebug", "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose (Trace) Logs?")), required: false, defaultValue: false, submitOnChange: true)
				input ("showDataChgdLogs", "bool", title: imgTitle(getAppImg("switch_on_icon.png"), inputTitleStr("Show API Data Changed in Logs?")), required: false, defaultValue: false, submitOnChange: true)
			} else {
				settingUpdate("advAppDebug", "false", "bool")
				settingUpdate("showDataChgdLogs", "false", "bool")
			}
		}
//		section (sectionTitleStr("Reset Application Data")) {
//			input (name: "resetAllData", type: "bool", title: imgTitle(getAppImg("reset_icon.png"), inputTitleStr("Reset Application Data?")), required: false, defaultValue: false, submitOnChange: true)
//		}
		if(settings?.appDebug) {
			if(getTimestampVal("debugEnableDt") == null) { updTimestampMap("debugEnableDt", getDtNow()) }
		} else { updTimestampMap("debugEnableDt", null) }
		state.needChildUpd = true

		section("App Info") {
			paragraph imgTitle(getAppImg("progress_bar.png"), "Current State Usage:\n${getStateSizePerc()}% (${getStateSize()} bytes)"), required: true, state: (getStateSizePerc() <= 70 ? "complete" : null)
			if(state?.isInstalled && state?.structures && (state?.thermostats || state?.protects || state?.cameras)) {
				input "enDiagWebPage", "bool", title: imgTitle(getAppImg("diagnostic_icon.png"), inputTitleStr("Enable Diagnostic Web Page?")), required: false, defaultValue: false, submitOnChange: true
/*
//device won't be created for a while so cannot do this now
				if(settings?.enDiagWebPage) {
					def t0 = getRemDiagApp()
					def t1 = t0.getAppEndpointUrl("diagHome")
					href url: t1, style:"external", title:"NST Diagnostic Web Page", description:"Tap to view", required: true, state: "complete", image: getAppImg("web_icon.png")
				}
*/
			}
		}
		if(getDevOpt()) {
			//settingUpdate("enDiagWebPage","true", "bool")
		}
		if(settings?.enDiagWebPage) {
			section("How's Does Log Collection Work:", hideable: true, hidden: true) {
				paragraph title: "How will the log collection work?", "When logs are enabled this App will create a child diagnostic app to store your logs which you can view under the diagnostics web page or share the url with the developer for remote troubleshooting.\n\n Turn off to remove the diag app and all data."
			}
			section("Log Collection:") {
/*
				def formatVal = settings?.useMilitaryTime ? "MMM d, yyyy - HH:mm:ss" : "MMM d, yyyy - h:mm:ss a"
				def tf = new SimpleDateFormat(formatVal)
				if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
*/
				paragraph "Logging will automatically turn off in 48 hours and all logs will be purged."
				//input ("showDataChgdLogs", "bool", title: imgTitle(getAppImg("switch_on_icon.png"), inputTitleStr("Show API Data Changed in Logs?")), required: false, defaultValue: false, submitOnChange: true)
				input ("enRemDiagLogging", "bool", title: imgTitle(getAppImg("log.png"), inputTitleStr("Enable Log Collection?")), required: false, defaultValue: (state?.enRemDiagLogging ?: false), submitOnChange: true)
				if(state?.enRemDiagLogging) {
					def str = "Press Done/Save all the way back to the main app page to allow the Diagnostic App to Install"
					paragraph str, required: true, state: "complete"
				}
			}
		}
		diagLogProcChange(settings?.enDiagWebPage)

	}
}


def getRemDiagApp() {
	if(settings?.enDiagWebPage) {
		def remDiagApp = getChildApps()?.find { it?.getAutomationType() == "remDiag" && it?.name == "NST Diagnostics" }
		if(remDiagApp) {
			//if(remDiagApp?.label != getRemDiagAppChildLabel()) { remDiagApp?.updateLabel(getRemDiagAppChildLabel()) }
			return remDiagApp
		}
		diagLogProcChange(settings?.enDiagWebPage)
	}
	return null
}

private void diagLogProcChange(setOn) {
	log.trace "diagLogProcChange($setOn)"
	boolean doInit = false
	String msg = "Remote Diagnostic Logs "

	boolean  mysetOn = (settings?.enDiagWebPage && settings?.enRemDiagLogging) ? true : false
	//log.trace "state: ${state?.enRemDiagLogging}   time:  ${getTimestampVal("remDiagLogActivatedDt")}"
	if(mysetOn) {
		if(!state?.enRemDiagLogging && getTimestampVal("remDiagLogActivatedDt") == null) {
			msg += "activated"
			doInit = true
			updTimestampMap("remDiagLogActivatedDt", getDtNow())
			state.enRemDiagLogging = true
			updTimestampMap("remDiagDataSentDt", getDtNow()) // allow us some time for child to start
		}
	} else {
		if(getTimestampVal("remDiagLogActivatedDt") != null || state?.enRemDiagLogging) {
			msg += "deactivated"
			settingUpdate("enRemDiagLogging", "false","bool")
			state.enRemDiagLogging = false
			updTimestampMap("remDiagLogActivatedDt", null)
			atomicState?.remDiagLogDataStore = []
			doInit = true
		}
	}

	if(  (state?.remDiagAppAvailable == true && !settings?.enDiagWebPage) ||
		(state?.remDiagAppAvaiable == false && settings?.enDiageWebPage == true) ) {
		initRemDiagApp() // create or delete as needed
	}

	if(doInit) {
		log.trace "diagLogProcChange: doInit"
		def kdata = getState()?.findAll { (it?.key in ["remDiagLogDataStore" /* , "remDiagDataSentDt"*/  ]) }
		kdata.each { kitem ->
			state.remove(kitem?.key.toString())
		}

		LogAction(msg, "info", true)
		if(!state?.enRemDiagLogging) { //when turning off, tell automations; turn on - user does done to this app
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
		updTimestampMap("lastAnalyticUpdDt", null)
	}
}

int getRemDiagActSec() { return getTimeSeconds("remDiagLogActivatedDt", 100000, "getRemDiagActSec") }
int getLastRemDiagSentSec() { return getTimeSeconds("remDiagDataSentDt", 1000, "getLastRemDiagSentSec") }


boolean getDevOpt() {
	return true
//	appSettings?.devOpt.toString() == "true" ? true : false
}

/******************************************************************************
 |						PAGE TEXT DESCRIPTION METHODS						|
 *******************************************************************************/
String getDevicesDesc(boolean startNewLine=true) {
	boolean pDev = settings?.thermostats || settings?.protects || settings?.cameras
	boolean vDev = settings?.vThermostats || settings?.presDevice
	String str = ""
	str += pDev ? "${startNewLine ? "\n" : ""}<b>Physical Devices:</b>" : ""
	str += settings?.thermostats ? "\n • <i>[${settings?.thermostats?.size()}] Thermostat${(settings?.thermostats?.size() > 1) ? "s" : ""}</i>" : ""
	str += settings?.protects ? "\n • <i>[${settings?.protects?.size()}] Protect${(settings?.protects?.size() > 1) ? "s" : ""}</i>" : ""
	str += settings?.cameras ? "\n • <i>[${settings?.cameras?.size()}] Camera${(settings?.cameras?.size() > 1) ? "s" : ""}</i>" : ""

	str += vDev ? "${pDev ? "\n" : ""}\n<b>Virtual Devices:</b>" : ""
	str += state?.vThermostats ? "\n • [${state?.vThermostats?.size()}] Virtual Thermostat${(state?.vThermostats?.size() > 1) ? "s" : ""}" : ""
	str += settings?.presDevice ? "\n • <i>Presence Device</i>" : ""
	str += settings?.weatherDevice ? "\n • <i>Weather Device Configured</i>" : ""
	str += (!settings?.thermostats && !settings?.protects && !settings?.cameras && !settings?.presDevice) ? "\n • <i>No Devices Selected</i>" : ""
	return (str != "") ? str : null
}

String getAppDebugDesc() {
	String str = ""
	str += isAppDebug() ? "App Debug: (${debugStatus()})${advAppDebug ? "(Trace)" : ""}" : ""
	str += settings?.showDataChgdLogs ? "${str ? "\n" : ""}Log API Changes: (${settings?.showDataChgdLogs ? "True" : "False"})" : ""
	str += getRemDiagDesc() ? "${str ? "\n" : ""}${getRemDiagDesc()}" : ""
	return (str != "") ? "${str}" : null
}


String getRemDiagDesc() {
	String str = ""
	str += settings?.enDiagWebPage ? "Web Page: (${settings.enDiagWebPage})" : ""
	if(settings?.enRemDiagLogging) {
		str += "\nLog Collection: (${settings.enRemDiagLogging})"
		String diagTime = (getTimestampVal("remDiagLogActivatedDt") != null) ? "\n• Will Disable in:\n  └ ${getDiagLogTimeRemaining()}" : "\n no time remaining found"
		str += diagTime
	}
	return (str != "") ? "${str}" : null
}


/******************************************************************************
 *								NEST LOGIN PAGES							*
 *******************************************************************************/
def nestLoginPrefPage () {
	if(!getNestAuthToken()) {
		return authPage()
	} else {
		return dynamicPage(name: "nestLoginPrefPage", title: "<h2>Nest Authorization Page</h2>", nextPage: getNestAuthToken() ? "" : "authPage", install: false) {
			updTimestampMap("authTokenCreatedDt", (getTimestampVal("authTokenCreatedDt") ?: getDtNow()))
			section() {
				paragraph "<b>Date Authorized:</b>\n• ${getTimestampVal("authTokenCreatedDt")}", state: "complete"
				if(getTimestampVal("lastDevDataUpd")) {
					paragraph "<b>Last API Connection:</b>\n• ${getTimestampVal("lastDevDataUpd")}"
				}
			}
			section(sectionTitleStr("Revoke Authorization Reset:")) {
				href "nestTokenResetPage", title: imgTitle(getAppImg("reset_icon.png"), inputTitleStr("Log Out and Reset Nest Token")), description: "<i>Tap to Reset Nest Token</i>", required: true, state: null
			}
		}
	}
}

def nestTokenResetPage() {
	return dynamicPage(name: "nestTokenResetPage", install: false) {
		section (sectionTitleStr("Resetting Nest Token")) {
			revokeNestToken()
			paragraph "Token Reset Complete...", state: "complete"
			paragraph "Press Done/Save to return to Login page"
		}
	}
}

String autoAppName()       { return "NST Automations" }

def automationsPage() {
	return dynamicPage(name: "automationsPage", title: "Installed Automations", nextPage: !parent ? "" : "automationsPage", install: false) {
		def autoApp = getChildApps()?.find { it?.name == autoAppName() || it?.name == "NST Graphs" || it?.name == "NST Diagnostics"}
		boolean autoAppInst = isAutoAppInst()
		if(autoApp) { /*Nothing to add here yet*/ }
		else {
			section("") {
				paragraph "You haven't created any Automations yet!\nTap Create New Automation to get Started"
			}
		}
		section("") {
			app(name: "autoApp", appName: autoAppName(), namespace: "tonesto7", multiple: true, title: imgTitle(getAppImg("nst_automations_5.png"), inputTitleStr("Create New Automation (NST)")))
			app(name: "autoApp", appName: "NST Graphs", namespace: "tonesto7", multiple: false, title: imgTitle(getAppImg("graph_icon.png"), inputTitleStr("Create Charts Automation")))
			app(name: "autoApp", appName: "NST Diagnostics", namespace: "tonesto7", multiple: false, title: imgTitle(getAppImg("diagnostic_icon.png"), inputTitleStr("Diagnostics Automation")))
		}
		if(autoAppInst) {
			section("Automation Details:") {
				def schEn = getChildApps()?.findAll { (!(it.getAutomationType() in ["nMode", "watchDog", "chart", "remDiag" ]) && it?.getActiveScheduleState()) }
				if(schEn?.size()) {
					href "automationSchedulePage", title: imgTitle(getAppImg("schedule_icon.png"), inputTitleStr("View Automation Schedule(s)")), description: ""
				}
			}
			section("Advanced Options: (Tap + to Show)		", hideable: true, hidden: true) {
/*
				def descStr = ""
				descStr += (settings?.locDesiredCoolTemp || settings?.locDesiredHeatTemp) ? "Comfort Settings:" : ""
				descStr += settings?.locDesiredHeatTemp ? "\n • Desired Heat Temp: (${settings?.locDesiredHeatTemp}${tUnitStr()})" : ""
				descStr += settings?.locDesiredCoolTemp ? "\n • Desired Cool Temp: (${settings?.locDesiredCoolTemp}${tUnitStr()})" : ""
				descStr += (settings?.locDesiredComfortDewpointMax) ? "${(settings?.locDesiredCoolTemp || settings?.locDesiredHeatTemp) ? "\n\n" : ""}Dew Point:" : ""
				descStr += settings?.locDesiredComfortDewpointMax ? "\n • Max Dew Point: (${settings?.locDesiredComfortDewpointMax}${tUnitStr()})" : ""
				descStr += "${(settings?.locDesiredCoolTemp || settings?.locDesiredHeatTemp) ? "\n\n" : ""}${getSafetyValuesDesc()}" ?: ""
				def prefDesc = (descStr != "") ? "${descStr}\n\nTap to modify" : "Tap to configure"
				href "automationGlobalPrefsPage", title: "Global Automation Preferences", description: prefDesc, state: (descStr != "" ? "complete" : null), image: getAppImg("global_prefs_icon.png")
*/
				input "disableAllAutomations", "bool", title: "Disable All Automations?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("disable_icon2.png")
				if(state?.disableAllAutomations == false && settings?.disableAllAutomations) {
					toggleAllAutomations(true)

				} else if (state?.disableAllAutomations && !settings?.disableAllAutomations) {
					toggleAllAutomations(true)
				}
				state.disableAllAutomations = settings?.disableAllAutomations == true ? true : false
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
			paragraph sectionTitleStr("$str"), state: "complete"
		}
		def schMap = []
		int schSize = 0
		getChildApps()?.each { capp ->
			def schedActMap = [:]
			def schInfo = capp?.getScheduleDesc()
			if (schInfo?.size()) {
				schSize = schSize+1
				def curSch = capp?.getCurrentSchedule()
				schInfo?.each { schItem ->
					section("${capp?.label}") {
						def schNum = schItem?.key
						String schDesc = schItem?.value
						boolean schInUse = (curSch?.toInteger() == schNum?.toInteger()) ? true : false
						if(schNum && schDesc) {
							paragraph "${schDesc}", state: schInUse ? "complete" : ""
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

private void toggleAllAutomations(boolean upd = false) {
	boolean t0 = settings?.disableAllAutomations == true ? true : false
	state.disableAllAutomations = t0
	String disStr = !t0 ? "Returning control to" : "Disabling"
	def cApps = getChildApps()
	cApps.each { ca ->
		LogAction("toggleAllAutomations: ${disStr} automation ${ca?.label}", "info", true)
		ca?.setAutomationStatus(upd)
	}
}


boolean isAutoAppInst() {
	int chldCnt = 0
	childApps?.each { cApp ->
		chldCnt = chldCnt + 1
	}
	return (chldCnt > 0) ? true : false
}

String getInstAutoTypesDesc() {
	def dat = ["nestMode":0, "watchDog":0, "chart": 0, "remDiag":0, "disabled":0, "schMot":["tSched":0, "remSen":0, "fanCtrl":0, "fanCirc":0, "conWat":0, "extTmp":0, "leakWat":0, "humCtrl":0 ]]
	def disItems = []
	def nItems = [:]
	def schMotItems = []
	childApps?.each { a ->
		String type
		def ver
		def dis
		try {
			type = a?.getAutomationType()
			dis = a?.getIsAutomationDisabled()
			ver = a?.appVersion()
		}
		catch(ex) {
			dis = null
			ver = null
			type = "old"
		}
		if(dis) {
			disItems.push(a?.label.toString())
			dat["disabled"] = dat["disabled"] ? dat["disabled"]+1 : 1
		} else {
			String tt1 = ""
			boolean clean = false
			switch(type) {
				case "nMode":
					tt1 = "nestMode"
					clean = true
					break
				case "schMot":
					def ai
					try {
						ai = a?.getAutomationsInstalled()
						schMotItems += a?.getSchMotConfigDesc(true)
					}
					catch (Exception e) {
						log.error "BAD Automation file ${a?.label?.toString()}, please RE-INSTALL automation file"
					}
					if(ai) {
						ai?.each { aut ->
							aut?.each { it2 ->
								if(it2?.key == "schMot") {
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
					updTimestampMap("lastAnalyticUpdDt", null)
					break
			}
			if(clean) {
				dat["${tt1}"] = dat["${tt1}"] ? dat["${tt1}"]+1 : 1
				if(dat."${tt1}" > 1) {
					dat."${tt1}" = dat."${tt1}" - 1
					LogAction("Deleting Extra ${tt1} (${a?.id})", "warn", true)
					deleteChildApp(a.id)
					updTimestampMap("lastAnalyticUpdDt", null)
				}
			}
		}
	}
	state.installedAutomations = dat

	String str = ""
	str += (dat?.watchDog > 0 || dat?.chart > 0 || dat?.nestMode > 0 || dat?.schMot || dat?.remDiag || dat?.disabled > 0) ? "Installed Automations:" : ""
	str += (dat?.watchDog > 0) ? "\n• Watchdog (Active)" : ""
	str += (dat?.chart > 0) ? "\n• Chart (Active)" : ""
	str += (dat?.remDiag > 0) ? "\n• Remote Diags (Active)" : ""
	str += (dat?.nestMode > 0) ? ((dat?.nestMode > 1) ? "\n• Nest Home/Away (${dat?.nestMode})" : "\n• Nest Home/Away (Active)") : ""
	def sch = dat?.schMot.findAll { it?.value > 0}
	str += (sch?.size()) ? "\n• Thermostat (${sch?.size()})" : ""
	int scii = 1
	def newList = schMotItems?.unique()
	newList?.sort()?.each { sci ->
		str += "${scii == newList?.size() ? "\n  └" : "\n  ├"} $sci"
		scii = scii+1
	}
	str += (disItems?.size() > 0) ? "\n• Disabled: (${disItems?.size()})" : ""
	return (str != "") ? str : null
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
	if(state?.needToFinalize == true) { LogAction("Skipping updated() as auth change in-progress", "warn", true); return }
	initialize()
	state.lastUpdatedDt = getDtNow()
}

void uninstalled() {
	LogTrace("uninstalled")
	try {
		state.access_token = null
		//Revokes Nest Auth Token
		revokeNestToken()
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

	if(getTimestampVal("debugEnableDt") == null) {
		updTimestampMap("debugEnableDt", getDtNow())
		settingUpdate("appDebug", "true",  "bool")
		runIn(600, logsOff)
	} else {
		if(settings?.appDebug || settings?.advAppDebug || settings?.showDataChgdLogs) { runIn(1800, logsOff) }
	}

	// force child update on next poll
	updTimestampMap("lastChildUpdDt", null)
	updTimestampMap("lastChildForceUpdDt", null)
	updTimestampMap("lastForcePoll", null)

	if(settings?.structures && state?.structures && !state?.structureName) {
		def structs = getNestStructures()
		if(structs && structs["${state?.structures}"]) {
			state.structureName = structs[state?.structures]?.toString()
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
	if(settings?.thermostats || settings?.protects || settings?.cameras || settings?.presDevice) {
		state.isInstalled = true
	} else { state?.isInstalled = false }
	subscriber()
	runIn(10, "finishUp", [overwrite: true])  // give time for devices to initialize

}

void finishUp() {
	LogTrace("finishUp")
	if(state?.isInstalled) { createSavedNest() }
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
	updTimestampMap("debugEnableDt", null)
}

int getDebugLogsOnSec() { return getTimeSeconds("debugEnableDt", 0, "getDebugLogsOnSec") }

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
	boolean keepApp = false
	boolean createApp = false
	String autoStr = ""
	switch (btype) {
		case "initNestModeApp":
			if(automationNestModeEnabled()) {
				keepApp = true
				autoStr = "nMode"
			}
			break
		case "initWatchdogApp":
			def t0 = settings?.thermostats?.size()
			def t1 = settings?.cameras?.size()
			if(state?.isInstalled && (t0 || t1)) {
				keepApp = true
				createApp = true
			}
			autoStr = "watchDog"
			break
		case "initRemDiagApp":
			if(settings?.enDiagWebPage ) {
				keepApp = true
				createApp = true
				remDiagAppAvail(true)
			} else {
				settingUpdate("enRemDiagLogging", "false","bool")
				remDiagAppAvail(false)
				state.enRemDiagLogging = false
				updTimestampMap("remDiagLogActivatedDt", null)
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
			updTimestampMap("lastAnalyticUpdDt", null)
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
			int cnt = 1
			mynestApp?.each { chld ->
				if(keepApp && cnt == 1) {
					LogTrace("initBuiltin: Running Update Command on ${autoStr}")
					chld.updated()
				} else if(!keepApp || cnt > 1) {
					String slbl = keepApp ? "warn" : "info"
					LogAction("initBuiltin: Deleting ${keepApp ? "Extra " : ""}${autoStr} (${chld?.id})", slbl, true)
					deleteChildApp(chld.id)
					updTimestampMap("lastAnalyticUpdDt", null)
				}
				cnt = cnt+1
			}
		}
	}
}

private String getWatDogAppChildLabel()    { return "${location.name} Watchdog" }
private String getRemDiagAppChildLabel()   { return "NST Location ${location.name} Diagnostics" }

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
	if(!state?.thermostats && !state?.protects && !state?.cameras && !state?.presDevice) {
		LogAction("No Devices are Installed | Polling is DISABLED", "info", true)
		unschedule("poll")
		state.pollingOn = false
		state.streamPolling = false
	} else {
		if(!getNestAuthToken()) {
			state.pollingOn = false
		}
		if(!state?.pollingOn && getNestAuthToken()) {
			//LogAction("Polling is ACTIVE", "info", true)
			state.pollingOn = true
			int pollTime = DevPoll() as Integer
			int pollStrTime = StrPoll() as Integer
			int theMax = 60
			if(restEnabled() && state?.restStreamingOn) {
				theMax = 300   // 5 minute poll checks
				state.streamPolling = true
			}
			pollTime = Math.max(pollTime, theMax)
			pollStrTime = Math.max(pollStrTime, theMax)
			int timgcd = gcd([pollTime, pollStrTime])
			def random = new Random()
			int random_int = random.nextInt(60)
			timgcd = (timgcd.toInteger() / 60) < 1 ? 1 : timgcd.toInteger() / 60
			int random_dint = random.nextInt(timgcd.toInteger())
			LogTrace("Next POLL scheduled (${random_int} ${random_dint}/${timgcd} * * * ?)")
			// this runs every timgcd minutes
			schedule("${random_int} ${random_dint}/${timgcd} * * * ?", poll)
			int timChk = state?.streamPolling ? 1200 : 240
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
	if((!restEnabled()) && !state?.restStreamingOn) {
		return
	}
	if(restEnabled() && state?.restStreamingOn) {
		runIn(30, "restStreamCheck", [overwrite: true])
		return
	}
	if(restEnabled() && !state?.restStreamingOn) {
		restStreamHandler(false, "startStopStream(start stream)")
		runIn(30, "restStreamCheck", [overwrite: true])
	}
}

def getStreamDevice() {
	return getChildDevice(getEventDeviceDni())
}

void restStreamHandler(boolean close = false, String src, boolean resetPoll=true) {
	LogAction("restStreamHandler(close: ${close}, src: ${src}), resetPoll: ${resetPoll}", "trace", true)
	def dev = getStreamDevice()
	if(!dev) {
		state.restStreamingOn = false;
		return
	} else {
		if(close) {
			state.restStreamingOn = false;
			dev?.streamStop()
			if(state?.streamPolling && resetPoll) {
				resetPolling()
			}
		} else {
			if(!getNestAuthToken()) {
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
	if(!getNestAuthToken()) {
		//LogAction("restStreamCheck: NestAuthToken Not Found!", "warn", false)
		return
	}
}

private gcd(a, b) {
	while (b > 0) {
		long temp = b
		b = a % b
		a = temp
	}
	return a
}

private gcd(input = []) {
	long result = input[0]
	for (int i = 1; i < input.size; i++) {
		result = gcd(result, input[i])
	}
	return result
}

def refresh(child = null) {
	def devId = !child?.device?.deviceNetworkId ? child?.toString() : child?.device?.deviceNetworkId.toString()
	//LogAction("Refresh Called by Device: (${child?.device?.displayName}", "debug", false)
	return sendNestCmd(state?.structures, "poll", "poll", 0, devId)
}

/************************************************************************************************
 |								API/Device Polling Methods										|
 *************************************************************************************************/

void pollFollow() { poll() }

void poll(boolean force = false, String type = null) {
	if(isPollAllowed()) {
		if(force == true) {
			forcedPoll(type)
			finishPoll()
			return
		}
		int pollTime = DevPoll() as Integer
		if(restEnabled() && state?.restStreamingOn) {
			pollTime = 300
		}
		int pollTimeout = pollTime*4 + 85
		int lastCheckin = getLastHeardFromNestSec()
		if(lastCheckin > pollTimeout) {
			if(restEnabled() && state?.restStreamingOn) {
				if(lastCheckin < 10000) {
					LogAction("We have not heard from Nest Stream in (${lastCheckin}sec.) | Stopping and Restarting Stream", "warn", true)
				}
				restStreamHandler(true, "poll", false)   // close the stream if we have not heard from it in a while
				//state?.restStreamingOn = false
			}
		}

		if(state?.streamPolling && (!restEnabled() || !state?.restStreamingOn)) {	// return to normal polling
			resetPolling()
			return
		}

		if(restEnabled() && state?.restStreamingOn) {
			LogTrace("Polling Skipped because Rest Streaming is ON")
			if(!state?.streamPolling) {	// set to stream polling
				resetPolling()
				return
			}
			finishPoll()
			return
		}
		runIn(5,"startStopStream", [overwrite: true])

		boolean okStruct = ok2PollStruct()
		boolean okDevice = ok2PollDevice()
		boolean okMeta = ok2PollMetaData()
		boolean meta = false
		boolean dev = false
		boolean str = false
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

void finishPoll(boolean str=false, boolean dev=false) {
	//LogTrace("finishPoll($str, $dev) received")
	if(state.pollBlocked) {
		LogAction("Polling BLOCKED | Reason: (${state.pollBlockedReason})", "trace", true);
		if(getLastAnyCmdSentSeconds() > 75) { // if poll is blocked and we have not sent a command recently, try to kick the queues
			schedNextWorkQ();
		}
		return
	}
	if(getLastChildForceUpdSec() > (15*60)-2) { // if nest goes silent (no changes coming back); force all devices to get an update so they can check health
		state.forceChildUpd = true
	}
	if(dev || str || state.forceChildUpd || state.needChildUpd) { updateChildData() }
	apiIssueNotify()
	if(state?.enRemDiagLogging && settings?.enRemDiagLogging) {
		saveLogtoRemDiagStore("", "", "", true) // force flush of remote logs
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

void schedFinishPoll(boolean devChg) {
	finishPoll(false, devChg)
	return
}

void forcedPoll(String type = null) {
	LogTrace("forcedPoll($type) received")
	int lastFrcdPoll = getLastForcedPollSec()
	int pollWaitVal = refreshWait()
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
		updTimestampMap("lastWebUpdDt", null)
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

void remDiagAppAvail(boolean available) {
	state.remDiagAppAvailable = (available == true)
}

void createSavedNest() {
	String str = "createSavedNest"
	LogTrace("${str}")
	if(state?.isInstalled) {
		def bbb = [:]
		boolean bad = false
		if(settings?.structures && state?.structures) {
			def structs = getNestStructures()
			String newStrucName = structs && structs?."${state?.structures}" ? "${structs[state?.structures]}" : null
			if(newStrucName) {
				bbb.a_structures_setting = settings.structures
				bbb.a_structures_as = state.structures
				bbb.a_structure_name_as = state?.structureName

				def dData = deviceDataFLD
				def t0 = [:]

				t0 = dData?.thermostats?.findAll { it?.key?.toString() in settings?.thermostats }
				LogAction("${str} | Thermostats(${t0?.size()}): ${settings?.thermostats}", "info", true)
				def t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1?."${devItem.key.toString()}" = devItem?.value?.name
					}
				}
				int t3 = settings?.thermostats?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Thermostat Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb?.b_thermostats_as = settings?.thermostats && dData && state?.thermostats ? t1 : [:]
				bbb?.b_thermostats_setting = settings?.thermostats ?: []

				dData = deviceDataFLD
				t0 = [:]
				t0 = dData?.smoke_co_alarms?.findAll { it?.key?.toString() in settings?.protects }
				LogAction("${str} | Protects(${t0?.size()}): ${settings?.protects}", "info", true)
				t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1."${devItem.key}" = devItem?.value?.name
					}
				}
				t3 = settings?.protects?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Protect Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb.c_protects_as = settings?.protects && dData && state?.protects ? t1 : [:]
				bbb.c_protects_settings = settings?.protects ?: []

				dData = deviceDataFLD
				t0 = [:]
				t0 = dData?.cameras?.findAll { it?.key?.toString() in settings?.cameras }
				LogAction("${str} | Cameras(${t0?.size()}): ${settings?.cameras}", "info", true)
				t1 = [:]
				t0?.each { devItem ->
					LogAction("${str}: Found (${devItem?.value?.name})", "info", false)
					if(devItem?.key && devItem?.value?.name) {
						t1."${devItem?.key}" = devItem?.value?.name
					}
				}
				t3 = settings?.cameras?.size() ?: 0
				if(t1?.size() != t3) { LogAction("Camera Counts Wrong! | Current: (${t1?.size()}) | Expected: (${t3})", "error", true); bad = true }
				bbb.d_cameras_as = settings?.cameras && dData && state?.cameras ? t1 : [:]
				bbb.d_cameras_setting = settings?.cameras ?: []
			} else { LogAction("${str}: No Structures Found!!!", "warn", true) }

			def t0 = state?.savedNestSettings ?: null
			String t1 = t0 ? new groovy.json.JsonOutput().toJson(t0) : null
			String t2 = bbb != [:] ? new groovy.json.JsonOutput().toJson(bbb) : null
			if(bad) {
				state.savedNestSettingsprev = state?.savedNestSettings
				state.savedNestSettingslastbuild = bbb
				state.remove("savedNestSettings")
			}
			if(!bad && t2 && (!t0 || t1 != t2)) {
				state.savedNestSettings = bbb
				state.remove("savedNestSettingsprev")
				state.remove("savedNestSettingslastbuild")
				return //true
			}
		} else { LogAction("${str}: No Structure Settings", "warn", true) }
	} else { LogAction("${str}: NOT Installed!!!", "warn", true) }
	return //false
}

void mySettingUpdate(String name, value, String type=null) {
	if(getDevOpt()) {
		LogAction("Setting $name set to type:($type) $value", "warn", true)
		if(!state?.ReallyChanged) { return }
	}
	if(state?.ReallyChanged) {
		settingUpdate(name, value, type)
	}
}

void checkRemapping() {
	String str = "checkRemapping"
	LogTrace(str)
	String astr = ""
	state.ReallyChanged = false
	boolean myRC = state.ReallyChanged
	if(state?.isInstalled && settings?.structures) {
		boolean aastr = getApiData("str")
		boolean aadev = getApiData("dev")
		//def aameta = getApiData("meta")
		def sData = state.structData
		def dData = deviceDataFLD
		//def mData = state?.metaData
		def savedNest = state?.savedNestSettings
		if(sData && dData /* && mData */ && savedNest) {
			def structs = getNestStructures()
			if(structs && !getDevOpt() ) {
				LogAction("${str}: nothing to do ${structs}", "info", true)
				return
			} else {
				astr += "${str}: found the mess..cleaning up ${structs}"
				state.pollBlocked = true
				state.pollBlockedReason = "Remapping"

				def newStructures_settings = ""
				def newThermostats_settings = []
				def newvThermostats = [:]
				def newProtects_settings = []
				def newCameras_settings = []
				String oldPresId = getNestPresId()

				sData?.each { strucId ->
					def t0 = strucId.key
					def t1 = strucId.value
					Logger("checkRempapping: t1.name: ${t1?.name?.toString()}   a_structure_name_as: ${savedNest?.a_structure_name_as?.toString()}", "info")
					if(t1?.name && t1?.name?.toString() == savedNest?.a_structure_name_as?.toString()) {
						newStructures_settings = [t1?.structure_id]?.join('.') as String
					}
				}
				Logger("checkRempapping: newStructures_settings: ${newStructures_settings?.toString()}", "info")
				if(settings?.structures && newStructures_settings) {
					if(settings.structures != newStructures_settings) {
						state.ReallyChanged = true
						myRC = state?.ReallyChanged
						astr += ", STRUCTURE CHANGED"
					} else {
						astr += ", NOTHING REALLY CHANGED (DEVELOPER MODE)"
					}
				} else { astr += ", no new structure found" }
				LogAction(astr, "warn", true)
				astr = ""
				if(myRC || (newStructures_setting && getDevOpt())) {
					mySettingUpdate("structures", newStructures_settings, "enum")
					if(myRC) { state?.structures = newStructures_settings }
					def newStrucName = newStructures_settings ? state?.structData[newStructures_settings]?.name : null
					astr = "${str}: newStructures ${newStructures_settings} | name: ${newStrucName} | to settings & as structures: ${settings?.structures}"

	//				astr += ",\n as.thermostats: ${state?.thermostats}  |  saveNest: ${savedNest?.b_thermostats_as}\n"
					LogAction(astr, "info", true)
					savedNest?.b_thermostats_as.each { dni ->
						def t0 = dni?.key
						def dev = getChildDevice(t0)
						if(dev) {
	//						LogAction("${str}: myRC : ${myRC}  found dev oldId: ${t0}", "info", true)
							boolean gotIt = false
							dData?.thermostats?.each { devItem ->
								def t21 = devItem.key
								def t22 = devItem.value
								def newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									def t6 = [t22?.device_id].join('.')
									def t7 = [ "${t6}":dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestTstatDni(ba)
									}
									newThermostats_settings << newDevId
									gotIt = true

									String rstr = "found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} |"
/*
									if(settings?."${t0}_safety_temp_min") {
										mySettingUpdate("${newDevId}_safety_temp_min", settings?."${t0}_safety_temp_min", "decimal")
										mySettingUpdate("${t0}_safety_temp_min", "")
										rstr += ", safety min"
									}
									if(settings?."${t0}_safety_temp_max") {
										mySettingUpdate("${newDevId}_safety_temp_max", settings?."${t0}_safety_temp_max", "decimal")
										mySettingUpdate("${t0}_safety_temp_max", "")
										rstr += ", safety max"
									}
									if(settings?."${t0}_comfort_dewpoint_max") {
										mySettingUpdate("${newDevId}_comfort_dewpoint_max", settings?."${t0}_comfort_dewpoint_max", "decimal")
										mySettingUpdate("${t0}_comfort_dewpoint_max", "")
										rstr += ", comfort dew"
									}
									if(settings?."${t0}_comfort_humidity_max") {
										mySettingUpdate("${newDevId}_comfort_humidity_max", settings?."${t0}_comfort_humidity_max", "number")
										mySettingUpdate("${t0}_comfort_humidity_max", "")
										rstr += ", comfort hum"
									}
*/
									if(settings?."tstat_${t0}_lbl") {
										if(state?.devNameOverride && state?.custLabelUsed) {
											mySettingUpdate("tstat_${newDevId}_lbl", settings?."tstat_${t0}_lbl", "text")
										}
										mySettingUpdate("tstat_${t0}_lbl", "")
										rstr += ", custom Label"
									}
									if(state?.vThermostats && state?."vThermostatv${t0}") {
										def physDevId = state?."vThermostatMirrorIdv${t0}"
										def t1 = state?.vThermostats
										def t5 = "v${newDevId}" as String

										if(t0 && t0 == physDevId && t1?."v${physDevId}") {
											def vdev = getChildDevice("v${t0}")
											if(vdev) {
												rstr += ", there are virtual devices that match"

												if(settings?."vtstat_v${t0}_lbl") {
													if(state?.devNameOverride && state?.custLabelUsed) {
														mySettingUpdate("vtstat_${t5}_lbl", settings?."tstat_v${t0}_lbl", "text")
													}
													mySettingUpdate("vtstat_v${t0}_lbl", "")
													rstr += ", custom vstat Label"
												}

												newvThermostats."${t5}" = t1."v${t0}"
												if(myRC) {
													state."vThermostat${t5}" = state?."vThermostatv${t0}"
													state."vThermostatMirrorId${t5}" = newDevId
													state."vThermostatChildAppId${t5}" = state?."vThermostatChildAppIdv${t0}"
												}

												def automationChildApp = getChildApps().find{ it.id == state?."vThermostatChildAppIdv${t0}" }
												if(automationChildApp != null) {
													if(myRC) { automationChildApp.setRemoteSenTstat(newDevId) }
													rstr += ", fixed state.remSenTstat"
												} else { rstr += ", DID NOT FIND AUTOMATION APP" }

												// fix locks
												def t3 = ""
												if(state?."remSenLock${t0}") {
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
					if(settings?.thermostats) {
						def t0 = settings?.thermostats?.size()
						def t1 = savedNest?.b_thermostats_as?.size()
						def t2 = newThermostats_settings?.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("thermostats", newThermostats_settings, "enum")
							astr += "${str}: myRC: ${myRC}  newThermostats_settings: ${newThermostats_settings} settings.thermostats: ${settings?.thermostats}"

							//LogAction("as.thermostats: ${state?.thermostats}", "warn", true)
							state.thermostats = null

							def t4 = newvThermostats ? newvThermostats?.size() : 0
							def t5 = state?.vThermostats ? state?.vThermostats.size() : 0
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
						def t0 = dni.key
						def dev = getChildDevice(t0)
						if(dev) {
							boolean gotIt = false
							dData?.smoke_co_alarms?.each { devItem ->
								astr = ""
								def t21 = devItem.key
								def t22 = devItem.value
								def newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									//def newDevId = [t22?.device_id].join('.')
									def t6 = [t22?.device_id].join('.')
									def t7 = [ "${t6}":dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestProtDni(ba)
									}
									newProtects_settings << newDevId
									gotIt = true
									astr += "${str}: myRC: ${myRC}  found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} "
									LogAction(astr, "info", true)

									if(settings?."prot_${t0}_lbl") {
										if(state?.devNameOverride && state?.custLabelUsed) {
											mySettingUpdate("prot_${newDevId}_lbl", settings?."prot_${t0}_lbl", "text")
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
					if(settings?.protects) {
						def t0 = settings?.protects?.size()
						def t1 = savedNest?.c_protects_as?.size()
						def t2 = newProtects_settings?.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("protects", newProtects_settings, "enum")
							astr += "newProtects: ${newProtects_settings} settings.protects: ${settings?.protects} "
							//LogAction("as.protects: ${state?.protects}", "warn", true)
							state.protects = null
						} else { LogAction("protect sizes don't match ${t0} ${t1} ${t2}", "warn", true) }
						LogAction(astr, "info", true)
					}

					astr = ""
					savedNest?.d_cameras_as.each { dni ->
						def t0 = dni.key
						def dev = getChildDevice(t0)
						if(dev) {
							boolean gotIt = false
							dData?.cameras?.each { devItem ->
								astr = ""
								def t21 = devItem.key
								def t22 = devItem.value
								def newDevStructId = [t22?.structure_id].join('.')
								if(!gotIt && t22 && newDevStructId && newDevStructId == newStructures_settings && dni.value == t22?.name) {
									//def newDevId = [t22?.device_id].join('.')
									def t6 = [t22?.device_id].join('.')
									def t7 = [ "${t6}":dni.value ]
									String newDevId
									t7.collect { ba ->
										newDevId = getNestCamDni(ba)
									}
									newCameras_settings << newDevId
									gotIt = true
									astr += "${str}: myRC: ${myRC}  found newDevId ${newDevId} to replace oldId: ${t0} ${t22?.name} "
									LogAction(astr, "info", true)

									if(settings?."cam_${t0}_lbl") {
										if(state?.devNameOverride && state?.custLabelUsed) {
											mySettingUpdate("cam_${newDevId}_lbl", settings?."cam_${t0}_lbl", "text")
										}
										mySettingUpdate("cam_${t0}_lbl", "")
									}

									if(myRC) { dev.deviceNetworkId = newDevId }
								}
							}
							if(!gotIt) { LogAction("${str}: NOT matched dev oldId: ${t0}", "warn", true) }
						} else { LogAction("${str}: NOT found dev oldId: ${t0}", "error", true) }
						def t10 = "oldCamData${t0}"
						state.remove(t10.toString())
					}
					astr = ""
					if(settings?.cameras) {
						def t0 = settings?.cameras?.size()
						def t1 = savedNest?.d_cameras_as?.size()
						def t2 = newCameras_settings?.size()
						if(t0 == t1 && t1 == t2) {
							mySettingUpdate("cameras", newCameras_settings, "enum")
							astr += "${str}: newCameras_settings: ${newCameras_settings} settings.cameras: ${settings?.cameras}"
							//LogAction("as.cameras: ${state?.cameras}", "warn", true)
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
					if(settings?.presDevice) {
						if(oldPresId) {
							def dev = getChildDevice(oldPresId)
							String newId = getNestPresId()
							def ndev = getChildDevice(newId)
							astr += "| DEV ${dev?.deviceNetworkId} | NEWID $newId |  NDEV: ${ndev?.deviceNetworkId} "
							def t10 = "oldPresData${dev?.deviceNetworkId}"
							state.remove(t10.toString())
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
					if(settings?.weatherDevice) {
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

				} else { LogAction("no changes or no data a:${settings?.structures} b: ${newStructures_settings}", "info", true) }

				state.pollBlocked = false
				state.pollBlockedReason = ""
				return
			}
		} else { LogAction("don't have our data", "warn", true) }
	} else { LogAction("not installed, no structure", "warn", true) }
}

void fixDevAS() {
	LogTrace("fixDevAS")
	if(settings?.thermostats && !state?.thermostats) { state.thermostats = settings?.thermostats ? statState(settings?.thermostats) : null }
	if(settings?.protects && !state?.protects) { state.protects = settings?.protects ? coState(settings?.protects) : null }
	if(settings?.cameras && !state?.cameras) { state.cameras = settings?.cameras ? camState(settings?.cameras) : null }
	state.presDevice = settings?.presDevice ?: null
	//state.weatherDevice = settings?.weatherDevice ?: null
}

private boolean getApiData(String type = (String)null) {
	//LogTrace("getApiData($type)")
	boolean result = false
	if(!type || !getNestAuthToken()) { return result }

	switch(type) {
		case "str":
		case "dev":
		case "meta":
			break
		default:
			return result
	}
	String tPath = (type == "str") ? "/structures" : ((type == "dev") ? "/devices" : "/")
	def params = [
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
				//state?.apiRateLimited = false
				//state?.apiCmdFailData = null
				if(type == "str") {
					def t0 = resp?.data
					//LogTrace("API Structure Resp.Data: ${t0}")
					if(state.structData == null) { state.structData = t0 }
					boolean chg = didChange(state.structData, t0, "str", "poll")
					if(chg) {
						result = true
						String newStrucName = state?.structData?.size() && state?.structures ? state?.structData[state?.structures]?.name : null
						state.structureName = newStrucName ?: state.structureName
					}
				}
				else if(type == "dev") {
					def t0 = resp?.data
					//LogTrace("API Device Resp.Data: ${t0}")
					boolean chg = didChange(deviceDataFLD, t0, "dev", "poll")
					if(chg) { result = true }
				}
				else if(type == "meta") {
					//LogTrace("API Metadata Resp.Data: ${resp?.data}")
					def nresp = resp?.data?.metadata
					boolean chg = didChange(state?.metaData, nresp, "meta", "poll")
					if(chg) { result = true }
				}
			} else {
				LogAction("getApiData - ${type} Received: Resp (${resp?.status})", "error", true)
				apiRespHandler(resp?.status, resp?.data, "getApiData(${type})", "${type} Poll")
				apiIssueEvent(true)
				state.forceChildUpd = true
			}
		}
	} catch (ex) {
		//state?.apiRateLimited = false
		state.forceChildUpd = true
		if(ex instanceof groovyx.net.http.HttpResponseException && ex?.response) {
			apiRespHandler(ex?.response?.status, ex?.response?.data, "getApiData(ex catch)", "${type} Poll")
		} else {
			if(type == "str") { state?.needStrPoll = true }
			else if(type == "dev") { state?.needDevPoll = true }
			else if(type == "meta") { state?.needMetaPoll = true }
		}
		apiIssueEvent(true)
		log.error "getApiData (type: $type) Exception: ${ex?.message}"
	}
	return result
}

def streamDeviceInstalled(val) { state?.streamDevice = val }

def eventStreamActive(Boolean val) { state?.eventStreamActive = val }

@Field static Map deviceDataFLD

void receiveEventData(evtData) {
	def status = [:]
//	try {
		// LogAction("evtData: $evtData", "trace", true)
		boolean devChgd = false
		boolean gotSomething = false
		if(evtData && evtData?.data && restEnabled()) {
			if(!state?.restStreamingOn) {
				state.restStreamingOn = true
			//	apiIssueEvent(false)
			}
			if(evtData?.data?.devices) {
				//LogTrace("API Device Resp.Data: ${evtData?.data?.devices}")
				gotSomething = true
				boolean chg = didChange(deviceDataFLD, evtData?.data?.devices, "dev", "stream")
				if(chg) {
					devChgd = true
				} //else { LogTrace("got deviceData") }
			}
			if(evtData?.data?.structures) {
				//LogTrace("API Structure Resp.Data: ${evtData?.data?.structures}")
				gotSomething = true
				boolean chg = didChange(state.structData, evtData?.data?.structures, "str", "stream")
				if(chg) {
					String newStrucName = state.structData && state?.structures ? state?.structData[state?.structures]?.name : null
					state.structureName = newStrucName ?: state.structureName
				} //else { LogTrace("got structData") }
			}
			if(evtData?.data?.metadata) {
				//LogTrace("API Metadata Resp.Data: ${evtData?.data?.metadata}")
				gotSomething = true
				boolean chg = didChange(state?.metaData, evtData?.data?.metadata, "meta", "stream")
				//if(!chg) { LogTrace("got metaData") }
			}
		} else {
			LogAction("Did not receive any data in stream response - likely stream shutdown", "warn", true)
			updTimestampMap("lastHeardFromNestDt", null)
			//apiIssueEvent(true)
//			if(state?.restStreamingOn) {
//				restStreamHandler(true, "receiveEventData(no data)")
//			}
//			state.restStreamingOn = false
			runIn(6, "pollFollow", [overwrite: true])
		}
		if(gotSomething) {
			updTimestampMap("lastHeardFromNestDt", getDtNow())
			//apiIssueEvent(false)
			//state?.apiRateLimited = false
			//state?.apiCmdFailData = null
		}
		if(state.forceChildUpd || state.needChildUpd || devChgd) {
			schedFinishPoll(devChgd)
		}
		status = ["data":"status received...ok", "code":200]
//	} catch (ex) {
//		log.error "receiveEventData Exception: ${ex?.message}"
//		status = ["data":"${ex?.message}", "code":500]
//	}
//
//	render contentType: 'text/html', data: status?.data, status: status?.code
}

boolean didChange(old, newer, String type, String src) {
	//LogTrace("didChange: type: $type  src: $src")
	boolean result = false
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
				def dtyp = t.key
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
				def tt0 = state?.structData?.size() ? state.structData : null
				// Null safe does not work on array references that miss
				def t0 = tt0 && state?.structures && tt0?."${state?.structures}" ?  tt0[state?.structures] : null
				def t1 = newer && state?.structures && newer?."${state?.structures}" ? newer[state?.structures] : null

				if(t1 && t0 != t1) {
					result = true
					state.forceChildUpd = true
					if(settings?.showDataChgdLogs == true && state?.enRemDiagLogging != true) {
						def chgs = getChanges(t0, t1, "/structures", "structure")
						if(chgs) { LogAction("STRUCTURE Data Changed ($srcStr): ${chgs}", "info", false) }
					} else {
						LogAction("Nest Structure Data HAS Changed ($srcStr)", "info", false)
					}
				}
				state.structData = newer
			}
			else if(type == "dev") {
				boolean devChg = false
				def tstats = state?.thermostats.collect { dni ->
					def t1 = dni.key
					if(t1 && old && old?.thermostats && newer?.thermostats && old?.thermostats[t1] && newer?.thermostats[t1] && old?.thermostats[t1] == newer?.thermostats[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old?.thermostats && newer?.thermostats && old?.thermostats[t1] && newer?.thermostats[t1]) {
							if(settings?.showDataChgdLogs == true && state?.enRemDiagLogging != true) {
								def chgs = getChanges(old?.thermostats[t1], newer?.thermostats[t1], "/devices/thermostats/${t1}", "thermostat")
								if(chgs) { LogAction("THERMOSTAT Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}

				def nProtects = state?.protects.collect { dni ->
					def t1 = dni.key
					if(t1 && old && old?.smoke_co_alarms && newer?.smoke_co_alarms && old?.smoke_co_alarms[t1] && newer?.smoke_co_alarms[t1] && old?.smoke_co_alarms[t1] == newer?.smoke_co_alarms[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old?.smoke_co_alarms && newer?.smoke_co_alarms && old?.smoke_co_alarms[t1] && newer?.smoke_co_alarms[t1]) {
							if(settings?.showDataChgdLogs == true && state?.enRemDiagLogging != true) {
								def chgs = getChanges(old?.smoke_co_alarms[t1], newer?.smoke_co_alarms[t1], "/devices/smoke_co_alarms/${t1}", "protect")
								if(chgs) { LogAction("PROTECT Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}

				def nCameras = state?.cameras.collect { dni ->
					def t1 = dni.key
					if(t1 && old && old?.cameras && newer?.cameras && old?.cameras[t1] && newer?.cameras[t1] && old?.cameras[t1] == newer?.cameras[t1]) {
						//Nothing to Do
					} else {
						result = true
						state.needChildUpd = true
						if(t1 && old && old?.cameras && newer?.cameras && old?.cameras[t1] && newer?.cameras[t1]) {
							if(settings?.showDataChgdLogs == true && state?.enRemDiagLogging != true) {
								def chgs = getChanges(old?.cameras[t1], newer?.cameras[t1], "/devices/cameras/${t1}", "camera")
								if(chgs) { LogAction("CAMERA Device Changed ($srcStr) | ${getChildDeviceLabel(t1)}: ${chgs}", "info", false) }
							} else { devChg = true }
						}
					}
				}
				if(devChg && (settings?.showDataChgdLogs != true)) { LogAction("Nest Device Data HAS Changed ($srcStr)", "info", false) }
				deviceDataFLD = newer
			}
			else if(type == "meta") {
				result = true
				state.needChildUpd = true
				state.metaData = newer
/*
				if(settings?.showDataChgdLogs != true) {
					LogAction("Nest MetaData HAS Changed ($srcStr)", "info", false)
				} else {
					def chgs = getChanges(old, newer, "/metadata", "metadata")
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

def getChanges(mapA, mapB, String headstr, String objType=null) {
	def t0 = mapA
	def t1 = mapB
	def left = t0
	def right = t1
	def itemsChgd = []
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
//						LogTrace("getChanges ${headstr} IT: ${it}  LEFT: ${left[it]}   RIGHT:${right[it]}")
						itemsChgd.push(it.toString())
					}
				}
			}
		}
		if(itemsChgd.size()) { return itemsChgd }
	}
	return null
}

private String generateMD5_A(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

void updateChildData(boolean force = false) {
	LogTrace("updateChildData(force: $force) | forceChildUpd: ${state.forceChildUpd} | needChildUpd: ${state.needChildUpd} | pollBlocked: ${state.pollBlocked}")
	if(state.pollBlocked) { return }
	boolean nforce = state?.forceChildUpd
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
		boolean restStreamingEn = restEnabled() != false
		if(settings?.devNameOverride == null /* || state?.useAltNames == null || state?.custLabelUsed == null */ ) { // Install / Upgrade force to on
			state.devNameOverride = true;
			settingUpdate("devNameOverride", "true", "bool")
			state.useAltNames = true
			settingUpdate("useAltNames", "true", "bool")
			state.custLabelUsed = false
			settingUpdate("useCustDevNames", "false", "bool")
		} else {
			state.devNameOverride = settings?.devNameOverride ? true : false
			if(state?.useAltNames == null || state?.custLabelUsed == null) {
				if(state?.devNameOverride) {
					state.useAltNames = settings?.useAltNames ? true : false
					state.custLabelUsed = settings?.useCustDevNames ? true : false
				} else {
					state.useAltNames = false
					state.custLabelUsed = false
				}
			}
		}

		boolean overRideNames = (state?.devNameOverride) ? true : false
		def devices = getChildDevices()
		devices?.each {
			if(state.pollBlocked) { return true }
			String devId = it?.deviceNetworkId
			if(devId && settings?.thermostats && deviceDataFLD?.thermostats && deviceDataFLD?.thermostats[devId]) {
				def tData = [data: deviceDataFLD?.thermostats[devId], tz: nestTz, apiIssues: api, pres: locPresence, childWaitVal: getChildWaitVal().toInteger(), etaBegin: locEtaBegin]

				String oldTstatData = state?."oldTstatData${devId}"
				String tDataChecksum = generateMD5_A(tData.toString())
				state."oldTstatData${devId}" = tDataChecksum
				tDataChecksum = state?."oldTstatData${devId}"

				if(tData && (force || nforce || oldTstatData != tDataChecksum)) {
					physDevLblHandler("thermostat", devId, it?.label, "thermostats", tData?.data?.name.toString(), "tstat", overRideNames)
					it?.generateEvent(tData)
				} else { /* LogTrace("tstat ${devId} did not change") */ }
				return true
			}
			else if(devId && settings?.protects && deviceDataFLD?.smoke_co_alarms && deviceDataFLD?.smoke_co_alarms[devId]) {
				def pData = [data: deviceDataFLD?.smoke_co_alarms[devId], showProtActEvts: (!showProtActEvts ? false : true), tz: nestTz, apiIssues: api ]
				String oldProtData = state?."oldProtData${devId}"
				String pDataChecksum = generateMD5_A(pData.toString())
				state?."oldProtData${devId}" = pDataChecksum
				pDataChecksum = state?."oldProtData${devId}"

				if(pData && (force || nforce || oldProtData != pDataChecksum)) {
					physDevLblHandler("protect", devId, it?.label, "protects", pData?.data?.name.toString(), "prot", overRideNames)
					it?.generateEvent(pData)
				} else { /* LogTrace("prot ${devId} did not change") */ }
				return true
			}
			else if(devId && settings?.cameras && deviceDataFLD?.cameras && deviceDataFLD?.cameras[devId]) {
				def camData = [data: deviceDataFLD?.cameras[devId], tz: nestTz, apiIssues: api, motionSndChgWaitVal: motionSndChgWaitVal, secState: locSecurityState ]
				String oldCamData = state?."oldCamData${devId}"
				String cDataChecksum = generateMD5_A(camData.toString())
				state?."oldCamData${devId}" = cDataChecksum
				cDataChecksum = state?."oldCamData${devId}"

				if(camData && (force || nforce || oldCamData != cDataChecksum)) {
					physDevLblHandler("camera", devId, it?.label, "cameras", camData?.data?.name.toString(), "cam", overRideNames)
					it?.generateEvent(camData)
				} else { /* LogTrace("cam ${devId} did not change") */ }
				return true
			}
			else if(devId && settings?.presDevice && devId == getNestPresId()) {
				def pData = [tz:nestTz, pres: locPresence, apiIssues: api, etaBegin: locEtaBegin, secState: locSecurityState, peakStart: locPeakStart, peakEnd: locPeakEnd ]
				String oldPresData = state?."oldPresData${devId}"
				String pDataChecksum = generateMD5_A(pData.toString())
				state?."oldPresData${devId}" = pDataChecksum
				pDataChecksum = state?."oldPresData${devId}"

				pData = [tz:nestTz, pres: locPresence, apiIssues: api, lastStrDataUpd: getTimestampVal("lastStrDataUpd"), etaBegin: locEtaBegin, secState: locSecurityState, peakStart: locPeakStart, peakEnd: locPeakEnd ]

				if(pData && (force || nforce || oldPresData != pDataChecksum)) {
					virtDevLblHandler(devId, it?.label, "pres", "pres", overRideNames)
					it?.generateEvent(pData)
				} else { /* LogTrace("pres ${devId} did not change") */ }
				return true
			}


			else if(devId && state?.vThermostats && state?."vThermostat${devId}") {
				def physdevId = state?."vThermostatMirrorId${devId}"
				if(physdevId && settings?.thermostats && deviceDataFLD?.thermostats && deviceDataFLD?.thermostats[physdevId]) {
					def tmp_data = deviceDataFLD?.thermostats[physdevId]
					def data = tmp_data
					def automationChildApp = getChildApps().find{ it.id == state?."vThermostatChildAppId${devId}" }
					if(automationChildApp != null && !automationChildApp.getIsAutomationDisabled()) {
						//data = new JsonSlurper().parseText(JsonOutput.toJson(tmp_data))  // This is a deep clone as object is same reference
						data = [:] + tmp_data  // This is a deep clone as object is same reference
						def tempC = 0.0
						def tempF = 0.0
						if(getTemperatureScale() == "C") {
							tempC = automationChildApp.getRemoteSenTemp()
							tempF = (tempC * (9 / 5) + 32.0)
						} else {
							tempF = automationChildApp.getRemoteSenTemp()
							tempC = (tempF - 32.0) * (5 / 9) as Double
						}
						data.ambient_temperature_c = tempC
						data.ambient_temperature_f = tempF

						def ctempC = 0.0
						def ctempF = 0
						if(getTemperatureScale() == "C") {
							ctempC = automationChildApp.getRemSenCoolSetTemp()
							ctempF = ctempC != null ? (ctempC * (9 / 5) + 32.0) as Integer : null
						} else {
							ctempF = automationChildApp.getRemSenCoolSetTemp()
							ctempC = ctempF != null ? (ctempF - 32.0) * (5 / 9) as Double : null
						}

						def htempC = 0.0
						def htempF = 0
						if(getTemperatureScale() == "C") {
							htempC = automationChildApp.getRemSenHeatSetTemp()
							htempF = htempC != null ? (htempC * (9 / 5) + 32.0) as Integer : null
						} else {
							htempF = automationChildApp.getRemSenHeatSetTemp()
							htempC = htempF != null ? (htempF - 32.0) * (5 / 9) as Double : null
						}

						if(data?.hvac_mode.toString() == "heat-cool") {
							data.target_temperature_high_f = ctempF
							data.target_temperature_low_f = htempF
							data.target_temperature_high_c = ctempC
							data.target_temperature_low_c = htempC
						} else if(data?.hvac_mode.toString() == "cool") {
							data.target_temperature_f = ctempF
							data.target_temperature_c = ctempC
						} else if(data?.hvac_mode.toString() == "heat") {
							data.target_temperature_f = htempF
							data.target_temperature_c = htempC
						}
					}


					def tData = [data: data, tz: nestTz, apiIssues: api, pres: locPresence, childWaitVal: getChildWaitVal().toInteger(), etaBegin: locEtaBegin, virt: true]

					String oldTstatData = state?."oldvstatData${devId}"
					String tDataChecksum = generateMD5_A(tData.toString())
					state?."oldvstatData${devId}" = tDataChecksum
					tDataChecksum = state?."oldvstatData${devId}"

					if(tData && (force || nforce || oldTstatData != tDataChecksum)) {
						physDevLblHandler("vthermostat", devId, it?.label, "vThermostats", tData?.data?.name.toString(), "vtstat", overRideNames)
						it?.generateEvent(tData)
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
		updTimestampMap("lastChildUpdDt", null)
		return
	}
	if(state.pollBlocked) { return }
	if(state.forceChildUpd) state.forceChildUpd = false
	if(state.needChildUpd)  state.needChildUpd = false
}

String tUnitStr() {
	return "\u00b0${getTemperatureScale()}"
}

private void setDeviceLabel(devId, labelStr) {
	def dev = getChildDevice(devId)
	if(labelStr) { dev.label = labelStr.toString() }
}

private void physDevLblHandler(String devType, devId, String devLbl, String devStateName, String apiName, String abrevStr, boolean ovrRideNames) {
	boolean nameIsDefault = false
	String deflbl
	String deflblval
	state?."${devStateName}"?.each { t ->
		if(t.key == devId) {
			deflblval = t.value
			deflbl = getDefaultLabel("${devType}", t.value)
		}
	}
	String  curlbl = devLbl?.toString()
	if(deflbl && deflbl == curlbl) { nameIsDefault = true }
	String newlbl = "getNest${abrevStr.capitalize()}Label"(apiName, devId)
	//LogTrace("physDevLblHandler | deflbl: ${deflbl} | curlbl: ${curlbl} | newlbl: ${newlbl} | deflblval: ${deflblval} || devId: ${devId}")
	if(ovrRideNames || (nameIsDefault && curlbl != newlbl)) {		// label change from nest
		if(curlbl != newlbl) {
			LogAction("Changing Name of Device from ${curlbl} to ${newlbl}", "info", true)
			setDeviceLabel(devId, newlbl?.toString())
			curlbl = newlbl?.toString()
		}
		def t0 = state?."${devStateName}"
		t0[devId] = apiName.toString()
		state?."${devStateName}" = t0
	}

	if(state?.custLabelUsed && settings?."${abrevStr}_${devId}_lbl" != curlbl) {
		settingUpdate("${abrevStr}_${devId}_lbl", curlbl?.toString())
	}
	if(!state?.custLabelUsed && settings?."${abrevStr}_${devId}_lbl") { settingUpdate("${abrevStr}_${devId}_lbl", "") }
	if(settings?."${abrevStr}_${deflblval}_lbl") { settingUpdate("${abrevStr}_${deflblval}_lbl", "") } // clean up old stuff

}

private void virtDevLblHandler(devId, String devLbl, String devMethAbrev, String abrevStr, boolean ovrRideNames) {
	String curlbl = devLbl
	String newlbl = "getNest${devMethAbrev.capitalize()}Label"()
	//LogTrace("virtDevLblHandler | curlbl: ${curlbl} | newlbl: ${newlbl} || devId: ${devId}")
	if(ovrRideNames && curlbl != newlbl) {
		LogAction("Changing Name of Device from ${curlbl} to ${newlbl}", "info", true)
		setDeviceLabel(devId, newlbl?.toString())
		curlbl = newlbl?.toString()
	}

	if(state?.custLabelUsed && settings?."${abrevStr}Dev_lbl" != curlbl) {
		settingUpdate("${abrevStr}Dev_lbl", curlbl?.toString())
	}
	if(!state?.custLabelUsed && settings?."${abrevStr}Dev_lbl") { settingUpdate("${abrevStr}Dev_lbl", "") }

}

def apiIssues() {
	def t0 = state?.apiIssuesList ?: [false, false, false, false, false, false, false]
	state.apiIssuesList = t0
	def result = t0[5..-1].every { it == true }  // last 2
	def dt = getTimestampVal("apiIssueDt")
	if(result) {
		def str = dt ? "may still be occurring. Status will clear when last updates are good (Last Updates: ${t0}) | Issues began at ($dt) " : "Detected (${getDtNow()})"
		LogAction("Nest API Issues ${str}", "warn", true)
	}
	return result
}

String apiIssueDesc() {
	String res = "Good"
	//this looks at the last 3 items added and determines whether issue is sporadic or outage
	def t0 = []
	t0 = state?.apiIssuesList ?: [false, false, false, false, false, false, false]
	state.apiIssuesList = t0
	def items = t0[3..-1].findAll { it == true }
	//LogTrace("apiIssueDesc: items: $items  t0: $t0")
	if(items?.size() >= 1 && items?.size() <= 2) { res = "Sporadic Issues" }
	else if(items?.size() >= 3) { res = "Full Outage" }
	//log.debug "apiIssueDesc: $res"
	return res
}

int issueListSize() { return 7 }

int getApiIssueSec() { return getTimeSeconds("apiIssueDt", 100000, "getApiIssueSec") }
int getLastApiIssueMsgSec() { return getTimeSeconds("lastApiIssueMsgDt", 100000, "getLastApiIssueMsgSec") }

private void apiIssueNotify() {
	if( (getApiIssueSec() > 600) && (getLastAnyCmdSentSeconds() > 600)) {
		updTimestampMap("apiIssueDt", null)
		state.apiIssuesList = []
		if(state?.apiRateLimited) {
			state.apiRateLimited = false
			LogAction("Clearing rate Limit", "info", true)
		}
	}

	if( !(getLastApiIssueMsgSec() > 900)) { return }
	boolean rateLimit = (state?.apiRateLimited) ? true : false
	boolean apiIssue = apiIssues() ? true : false // any recent API issues
	if(apiIssue || rateLimit) {
		String msg = ""
		msg += apiIssue ? "\nThe Nest API appears to be having issues. This will effect the updating of device and location data.\nThe issues started at (${getTimestampVal("apiIssueDt")})" : ""
		msg += rateLimit ? "${apiIssue ? "\n\n" : "\n"}Your API connection is currently being Rate-limited for excessive commands." : ""
		if(sendMsg("${app?.label} API Issue Warning", msg, 1)) {
			updTimestampMap("lastApiIssueMsgDt", getDtNow())
		}
	}
}

int getLastFailedCmdMsgSec() { return getTimeSeconds("lastFailedCmdMsgDt", 100000, "getLastFailedCmdMsgSec") }

private void failedCmdNotify(failData, tstr) {
	if(!(getLastFailedCmdMsgSec() > 300)) { return }
	boolean cmdFail = (failData?.msg != null) ? true : false
	String cmdstr = tstr ?: state?.lastCmdSent
	String msg = "\nThe (${cmdstr}) CMD sent to the API has failed.\nStatus Code: ${failData?.code}\nErrorMsg: ${failData?.msg}\nDT: ${failData?.dt}"
	if(cmdFail) {
		if(sendMsg("${app?.label} API CMD Failed", msg, 1)) {
			updTimestampMap("lastFailedCmdMsgDt", getDtNow())
		}
	}
	LogAction(msg, (cmdFail ? "error" : "warn"), true)
}

private void apiIssueEvent(issue, cmd = null) {
	def list = state?.apiIssuesList ?: [false, false, false, false, false, false, false]
	int listSize = issueListSize()
	if(list?.size() < listSize) {
		list.push(issue)
	}
	else if(list?.size() > listSize) {
		int nSz = (list?.size()-listSize) + 1
		def nList = list?.drop(nSz)
		nList?.push(issue)
		list = nList
	}
	else if(list?.size() == listSize) {
		def nList = list?.drop(1)
		nList?.push(issue)
		list = nList
	}
	state?.apiIssuesList = list
	if(issue) {
		if(!getTimestampVal("apiIssueDt")) {
			updTimestampMap("apiIssueDt", getDtNow())
		}
	} else {
		def result = list[3..-1].every { it == false }
		boolean rateLimit = (state?.apiRateLimited) ? true : false
		if(rateLimit) {
			int t0 = state?.apiCmdFailData?.dt ? GetTimeDiffSeconds(state?.apiCmdFailData?.dt, null, "apiIssueEvent").toInteger() : 200
			if((t0 > 120 && result) || t0 > 500) {
				state?.apiRateLimited = false
				rateLimit = false
				LogAction("Clearing rate Limit", "info", true)
			}
		}
	}
}

private boolean ok2PollMetaData() {
	return pollOk("Meta")
}

private boolean ok2PollDevice() {
	return pollOk("Dev")
}

private boolean ok2PollStruct() {
	return (pollOk("Str") || !state.structData) ? true : false
}

private boolean pollOk(String typ) {
	if(!getNestAuthToken()) { return false }
	if(state.pollBlocked) { return false }
	if(state."need${typ}Poll") { return true }
	int pollTime = "${typ}Poll"() as Integer
	int val = pollTime / 3
	val = Math.max(Math.min(val.toInteger(), 50),25)
	return ( (("getLast${typ}PollSec"() + val) > pollTime) ? true : false )
}


private boolean isPollAllowed() {
	return (state.pollingOn && getNestAuthToken() && (settings.thermostats || settings.protects || settings.cameras || settings.presDevice)) ? true : false
}

int getLastMetaPollSec() { return getTimeSeconds("lastMetaDataUpd", 100000, "getLastMetaPollSec") }
int getLastDevPollSec() { return getTimeSeconds("lastDevDataUpd", 840, "getLastDevPollSec") }
int getLastStrPollSec() { return getTimeSeconds("lastStrDataUpd", 1000, "getLastStrPollSec") }
int getLastForcedPollSec() { return getTimeSeconds("lastForcePoll", 1000, "getLastForcedPollSec") }
int getLastChildUpdSec() { return getTimeSeconds("lastChildUpdDt", 100000, "getLastChildUpdSec") }
int getLastChildForceUpdSec() { return getTimeSeconds("lastChildForceUpdDt", 100000, "getLastChildForceUpdSec") }
int getLastHeardFromNestSec() { return getTimeSeconds("lastHeardFromNestDt", 100000, "getLastHeardFromNestSec") }

/************************************************************************************************
 |										Nest API Commands										|
 *************************************************************************************************/

private cmdProcState(Boolean value) { atomicState?.cmdIsProc = value }
private cmdIsProc() { return (!atomicState?.cmdIsProc) ? false : true }
private int getLastProcSeconds() { return getTimeSeconds("cmdLastProcDt", 0, "getLastProcSeconds") }

def apiVar() {
	def api = [
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
def getPdevId(Boolean virt, devId) {
	def pChild
	if(virt && state?.vThermostats && devId) {
		if(state?."vThermostat${devId}") {
			def pdevId = state?."vThermostatMirrorId${devId}"
			if(pdevId) { pChild = getChildDevice(pdevId) }
			if(pChild) { return pChild }
			else { return "00000" }
		}
	}
	return pChild
}

def setEtaState(child, etaData, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? child?.toString() : child?.device?.deviceNetworkId.toString()

	def str1 = "setEtaState | "
	def strAction = "BAD data"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) | Trip_Id: ${etaData?.trip_id} | Begin: ${etaData?.estimated_arrival_window_begin} | End: ${etaData?.estimated_arrival_window_end}"

	if(etaData?.trip_id && etaData?.estimated_arrival_window_begin && etaData?.estimated_arrival_window_end) {
		def etaObj = [ "trip_id":"${etaData.trip_id}", "estimated_arrival_window_begin":"${etaData.estimated_arrival_window_begin}", "estimated_arrival_window_end":"${etaData.estimated_arrival_window_end}" ]
		// "trip_id":"sample-trip-id","estimated_arrival_window_begin":"2014-10-31T22:42:00.000Z","estimated_arrival_window_end":"2014-10-31T23:59:59.000Z"
		// new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

		strAction = "Setting Eta"
		def pChild = getPdevId(virtual.toBoolean(), devId)
		if(pChild == null) {
			LogAction(str1+strAction+strArgs, "debug", true)
			return sendNestCmd(state?.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.eta, etaObj, devId)
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

def cancelEtaState(child, trip_id, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? child?.toString() : child?.device?.deviceNetworkId.toString()

	def str1 = "cancelEtaState | "
	def strAction = "BAD data"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) | Trip_Id: ${trip_id}"

	if(trip_id) {
		def etaObj = [ "trip_id":"${trip_id}", "estimated_arrival_window_begin":0, "estimated_arrival_window_end":0 ]
		// "trip_id":"sample-trip-id","estimated_arrival_window_begin":"2014-10-31T22:42:00.000Z","estimated_arrival_window_end":"2014-10-31T23:59:59.000Z"
		// new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

		strAction = "Cancel Eta"
		def pChild = getPdevId(virtual.toBoolean(), devId)
		if(pChild == null) {
			LogAction(str1+strAction+strArgs, "debug", true)
			return sendNestCmd(state?.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.eta, etaObj, devId)
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

def setCamStreaming(child, streamOn) {
	def devId = !child?.device?.deviceNetworkId ? child?.toString() : child?.device?.deviceNetworkId.toString()
	def val = streamOn.toBoolean() ? true : false
	LogAction("setCamStreaming | Setting Camera (${child?.device?.displayName} - ${devId}) Streaming to (${val ? "On" : "Off"})", "debug", true)
	return sendNestCmd(devId, apiVar().rootTypes.cam, apiVar().cmdObjs.streaming, val, devId)
}

def setStructureAway(child, value, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()
	def val = value?.toBoolean()

	def str1 = "setStructureAway | "
	def strAction = ""
	strAction = "Setting Nest Location:"
	def strArgs = " (${child?.device?.displayName} ${!devId ? "" : "-  ${devId}"} to (${val ? "Away" : "Home"})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
	if(pChild == null) {
		LogAction(str1+strAction+strArgs, "debug", true)
		if(val) {
			def ret = sendNestCmd(state?.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.away, "away", devId)
			// Below is to ensure automations read updated value even if queued
			if(ret && state.structData && state?.structures && state?.structData[state?.structures]?.away) {
				def t0 = state.structData
				t0[state?.structures].away = "away"
				state.structData = t0
			}
			return ret
		}
		else {
			def ret = sendNestCmd(state?.structures, apiVar().rootTypes.struct, apiVar().cmdObjs.away, "home", devId)
			if(ret && state.structData && state?.structures && state?.structData[state?.structures]?.away) {
				def t0 = state.structData
				t0[state?.structures].away = "home"
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
			return
		} else {
			strAction = "CANNOT Set Location"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
}

def setFanMode(child, fanOn, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()
	def val = fanOn.toBoolean()

	def str1 = "setFanMode | "
	def strAction = ""
	strAction = "Setting"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Fan Mode to (${val ? "On" : "Auto"})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
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
			return
		} else {
			strAction = "CANNOT Set"
		}
	}
	LogAction(str1+strAction+strArgs, "warn", true)
}

def setHvacMode(child, mode, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()

	def str1 = "setHvacMode | "
	def strAction = ""
	strAction = "Setting"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) HVAC Mode to (${mode})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
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
			return
		} else {
			strAction = "CANNOT Set "
		}
	}

	LogAction(str1+strAction+strArgs, "warn", true)
}

def setTargetTemp(child, unit, temp, mode, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()

	def str1 = "setTargetTemp | "
	def strAction = ""
	strAction = "Setting"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
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
		def appId = state?."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }
		if(automationChildApp) {
			def res = automationChildApp.remSenTempUpdate(temp,mode)
			if(res) { return }
		}
		if(pChild != "00000") {
			if(mode == 'cool') {
				pChild.setCoolingSetpoint(temp)
			} else if(mode == 'heat') {
				pChild.setHeatingSetpoint(temp)
			} else { LogAction("setTargetTemp - UNKNOWN MODE (${mode}) child ${pChild}", "warn", true) }
			return
		} else {
			strAction = "CANNOT Set"
		}
	}

	LogAction(str1+strAction+strArgs, "warn", true)
}

def setTargetTempLow(child, unit, temp, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()

	def str1 = "setTargetTempLow | "
	def strAction = ""
	strAction = "Setting"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp Low to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
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
		def appId = state?."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }

		if(automationChildApp) {
			def res = automationChildApp.remSenTempUpdate(temp,"heat")
			if(res) { return }
		}
		if(pChild != "00000") {
			pChild.setHeatingSetpoint(temp)
			return
		} else {
			strAction = "CANNOT Set"
		}
	}

	LogAction(str1+strAction+strArgs, "warn", true)
}

def setTargetTempHigh(child, unit, temp, virtual=false) {
	def devId = !child?.device?.deviceNetworkId ? null : child?.device?.deviceNetworkId.toString()

	def str1 = "setTargetTempHigh | "
	def strAction = ""
	strAction = "Setting"
	def strArgs = " ${virtual ? "Virtual " : ""}Thermostat (${child?.device?.displayName} - ${devId}) Target Temp High to (${temp}${tUnitStr()})"

	def pChild = getPdevId(virtual.toBoolean(), devId)
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
		def appId = state?."vThermostatChildAppId${devId}"
		def automationChildApp
		if(appId) { automationChildApp = getChildApps().find{ it?.id == appId } }

		if(automationChildApp) {
			def res = automationChildApp.remSenTempUpdate(temp,"cool")
			if(res) { return }
		}
		if(pChild != "00000") {
			pChild.setCoolingSetpoint(temp)
			return
		} else {
			strAction = "CANNOT Set"
		}
	}

	LogAction(str1+strAction+strArgs, "warn", true)
}

def sendNestCmd(cmdTypeId, cmdType, cmdObj, cmdObjVal, childId) {
	// LogAction("sendNestCmd $cmdTypeId, $cmdType, $cmdObj, $cmdObjVal, $childId", "info", true)
	if(!getNestAuthToken()) {
		LogAction("sendNestCmd Error | Nest Auth Token Not Found", "warn", true)
		return false
	}
	try {
		if(cmdTypeId) {
			def qnum = getQueueNumber(cmdTypeId)
			if(qnum == -1 ) { return false }

			state.pollBlocked = true
			state.pollBlockedReason = "Sending Cmd"
			def now = new Date()
			def cmdData = [cmdTypeId?.toString(), cmdType?.toString(), cmdObj?.toString(), cmdObjVal, now]

			def tempQueue = []
			def newCmd = []
			def replaced = false
			def skipped = false
			def schedQ = false

			if(!atomicState?."cmdQ${qnum}" ) { atomicState?."cmdQ${qnum}" = [] }
			def cmdQueue = atomicState?."cmdQ${qnum}"
			cmdQueue.each { cmd ->
				if(newCmd != []) {
					tempQueue << newCmd
				}
				newCmd = [cmd[0], cmd[1], cmd[2], cmd[3], cmd[4]]
			}

			if(newCmd != []) {		// newCmd is last command in queue
				if(newCmd[1] == cmdType?.toString() && newCmd[2] == cmdObj?.toString() && newCmd[3] == cmdObjVal) {	// Exact same command; leave it and skip
					skipped = true
					tempQueue << newCmd
				} else if(newCmd[1] == cmdType?.toString() && newCmd[2] == cmdObj?.toString() &&
						newCmd[2] != apiVar().cmdObjs.away && newCmd[2] != apiVar().cmdObjs.fanActive && newCmd[2] != apiVar().cmdObjs.fanTimer && newCmd[2] != apiVar().cmdObjs.eta) {
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
			atomicState?."cmdQ${qnum}" = tempQueue

			def str = "Adding"
			if(replaced) { str = "Replacing" }
			if(skipped) { str = "Skipping" }

			if(replaced || skipped) {
				LogAction("Command Matches the Last item in Queue ${qnum} - ${str}", "warn", true)
			}

			LogAction("${str} Cmd to Queue [${qnum}] (Queued Items: ${tempQueue?.size()}): $cmdTypeId, $cmdType, $cmdObj, $cmdObjVal, $childId", "info", true)
			state?.lastQcmd = cmdData
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
private getQueueNumber(cmdTypeId) {
	if(!atomicState.cmdQlist) { atomicState.cmdQlist = [] }
	def cmdQueueList = atomicState.cmdQlist
	def qnum = cmdQueueList.indexOf(cmdTypeId)
	if(qnum == -1) {
		cmdQueueList = atomicState.cmdQlist
		cmdQueueList << cmdTypeId
		atomicState.cmdQlist = cmdQueueList
		qnum = cmdQueueList.indexOf(cmdTypeId)
		atomicState?."cmdQ${qnum}" = null
		setLastCmdSentSeconds(qnum, null)
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
def getQueueToWork() {
	def qnum
	def savedtim
	if(!atomicState.cmdQlist) { atomicState.cmdQlist = [] }
	def cmdQueueList = atomicState.cmdQlist
	cmdQueueList.eachWithIndex { val, idx ->
		def cmdQueue = atomicState?."cmdQ${idx}"
		if(cmdQueue?.size() > 0) {
			def cmdData = cmdQueue[0]
			def timVal = cmdData[4]
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

private cmdMaxVal() { return 2 }

void schedNextWorkQ(useShort=false) {
	int cmdDelay = getChildWaitVal()
	if(useShort) { cmdDelay = 0 }
	//
	// This is throttling the rate of commands to the Nest service for this access token.
	// If too many commands are sent Nest throttling could shut all write commands down for 1 hour to the device or structure
	// This allows up to 3 commands if none sent in the last hour, then only 1 per 60 seconds. Nest could still
	// throttle this if the battery state on device is low.
	// https://nestdevelopers.io/t/user-receiving-exceeded-rate-limit-on-requests-please-try-again-later/354
	//

	def qnum = getQueueToWork()
	int timeVal = cmdDelay
	String str = ""
	int queueItemsAvail = 0
	int lastCommandSent = 0
	if(qnum != null) {
		queueItemsAvail = getRecentSendCmd(qnum)
		lastCommandSent = getLastCmdSentSeconds(qnum)
		if( (queueItemsAvailable == 0 && lastCommandSent > 60) ) { queueItemsAvail = 1 }
		if( queueItemsAvail <= 0 || state?.apiRateLimited) {
			timeVal = 60 + cmdDelay
		} else if(lastCommandSent < 60) {
			timeVal = (60 - lastCommandSent + cmdDelay)
			if(queueItemsAvail > 0) { timeVal = 0 }
		}
		str = timeVal > cmdDelay || state?.apiRateLimited ? "*RATE LIMITING ON* " : ""
		//LogAction("schedNextWorkQ │ ${str}queue: ${qnum} │ schedTime: ${timeVal} │ recentSendCmd: ${queueItemsAvail} │ last seconds: ${lastCommandSent} │ cmdDelay: ${cmdDelay} | runInActive: ${atomicState.workQrunInActive} | Api Limited: ${state?.apiRateLimited}", "info", true)
	} else {
		return //timeVal = 0
	}
	String actStr = "ALREADY PENDING "
	if(cmdIsProc()) { actStr = "COMMAND RUNNING " }
	if(!atomicState.workQrunInActive && !cmdIsProc() ) {
		atomicState.workQrunInActive = true
		if(timeVal != 0) {
			actStr = "RUNIN "
			runIn(timeVal.toInteger(), "workQueue", [overwrite: true])
		} else {
			actStr = "DIRECT CALL "
			workQueue()
		}
	}
	LogAction("schedNextWorkQ ${actStr} │ ${str}queue: ${qnum} │ schedTime: ${timeVal} │ recentSendCmd: ${queueItemsAvail} │ last seconds: ${lastCommandSent} │ cmdDelay: ${cmdDelay} | runInActive: ${atomicState.workQrunInActive} | command proc: ${cmdIsProc()} | Api Limited: ${state?.apiRateLimited}", "info", true)
}

private int getRecentSendCmd(qnum) {
	return atomicState?."recentSendCmd${qnum}"
}

private void setRecentSendCmd(qnum, val) {
	atomicState?."recentSendCmd${qnum}" = val
	return
}

def sendEcoActionDescToDevice(dev, desc) {
	if(dev && desc) {
		dev?.ecoDesc(desc)
	}
}

private int getLastAnyCmdSentSeconds() { return getTimeSeconds("lastCmdSentDt", 3601, "getLastAnyCmdSentSeconds") }
private int getLastCmdSentSeconds(qnum) { return getTimeSeconds("lastCmdSentDt${qnum}", 3601, "getLastCmdSentSeconds") }

private void setLastCmdSentSeconds(qnum, val) {
	updTimestampMap("lastCmdSentDt${qnum}", val)
	updTimestampMap("lastCmdSentDt", val)
}

/*
void storeLastCmdData(cmd, qnum) {
	if(cmd) {
		def newVal = ["qnum":qnum, "obj":cmd[2], "value":cmd[3], "date":getDtNow()]

		def list = state?.cmdDetailHistory ?: []
		int listSize = 30
		if(list?.size() < listSize) {
			list.push(newVal)
		}
		else if(list?.size() > listSize) {
			int nSz = (list?.size()-listSize) + 1
			def nList = list?.drop(nSz)
			nList?.push(newVal)
			list = nList
		}
		else if(list?.size() == listSize) {
			def nList = list?.drop(1)
			nList?.push(newVal)
			list = nList
		}
		if(list) { state?.cmdDetailHistory = list }
	}
}
*/

void workQueue() {
	LogTrace("workQueue")
	atomicState.workQrunInActive = false
	//def cmdDelay = getChildWaitVal()
	if(!atomicState.cmdQlist) { atomicState.cmdQlist = [] }
	def cmdQueueList = atomicState.cmdQlist

	def qnum = getQueueToWork()
	if(qnum == null) { qnum = 0 }
	if(!atomicState?."cmdQ${qnum}") { atomicState?."cmdQ${qnum}" = [] }

	def cmdQueue = atomicState?."cmdQ${qnum}"
	try {
		if(cmdQueue?.size() > 0) {
			LogTrace("workQueue │ Run Queue: ${qnum}")
			runIn(90, "workQueue", [overwrite: true])  //lost schedule catchall
			if(!cmdIsProc()) {
				cmdProcState(true)
				state.pollBlocked = true
				state.pollBlockedReason = "Processing Queue"
				cmdQueue = atomicState?."cmdQ${qnum}"
				// log.trace "cmdQueue(workqueue): $cmdQueue"
				def cmd = cmdQueue?.remove(0)
				// log.trace "cmdQueue(workqueue-after): $cmdQueue"
				// log.debug "cmd: $cmd"
				atomicState?."cmdQ${qnum}" = cmdQueue
				def cmdres

				if(getLastCmdSentSeconds(qnum) > 3600) { setRecentSendCmd(qnum, cmdMaxVal()) } // if nothing sent in last hour, reset command limit

				// storeLastCmdData(cmd, qnum)

				if(cmd[1] == "poll") {
					state?.needStrPoll = true
					state?.needDevPoll = true
					state?.forceChildUpd = true
					cmdres = true
				} else {
					//cmdres = procNestCmd(getNestApiUrl(), cmd[0], cmd[1], cmd[2], cmd[3], qnum)
					cmdres = queueProcNestCmd(getNestApiUrl(), cmd[0], cmd[1], cmd[2], cmd[3], qnum, cmd)
					return
				}
				finishWorkQ(cmd, cmdres)
			} else { LogAction("workQueue: busy processing command", "warn", true) }
		} else { state.pollBlocked = false; state.remove("pollBlockedReason"); cmdProcState(false); }
	}
	catch (ex) {
		log.error "workQueue Exception Error: ${ex?.message}"
		cmdProcState(false)
		state.needDevPoll = true
		state.needStrPoll = true
		state.forceChildUpd = true
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		atomicState.workQrunInActive = true
		runIn(60, "workQueue", [overwrite: true])
		runIn(64, "postCmd", [overwrite: true])
		return
	}
}

def finishWorkQ(cmd, result) {
	LogTrace("finishWorkQ cmd: $cmd  result: $result")
	int cmdDelay = getChildWaitVal()

	if( !result ) {
		state.forceChildUpd = true
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		runIn((cmdDelay * 3).toInteger(), "postCmd", [overwrite: true])
	}

	state?.needDevPoll = true
	if(cmd && cmd[1] == apiVar().rootTypes.struct.toString()) {
		state?.needStrPoll = true
		state?.forceChildUpd = true
	}

	updTimestampMap("cmdLastProcDt", getDtNow())
	cmdProcState(false)

	def qnum = getQueueToWork()
	if(qnum == null) { qnum = 0 }
	if(!atomicState?."cmdQ${qnum}") { atomicState?."cmdQ${qnum}" = [] }

	def cmdQueue = atomicState?."cmdQ${qnum}"
	if(cmdQueue?.size() == 0) {
		state.pollBlocked = false
		state.remove("pollBlockedReason")
		state.needChildUpd = true
		runIn(cmdDelay.toInteger(), "postCmd", [overwrite: true])
	}
	else { schedNextWorkQ(true) }

	if(cmdQueue?.size() > 10) {
		sendMsg("Warning", "There is now ${cmdQueue?.size()} events in the Command Queue. Something must be wrong", 1)
		LogAction("${cmdQueue?.size()} events in the Command Queue", "warn", true)
	}
	return
}

def queueProcNestCmd(uri, typeId, type, obj, objVal, qnum, cmd, redir = false) {
	String myStr = "queueProcNestCmd"
	LogTrace("${myStr}: typeId: ${typeId}, type: ${type}, obj: ${obj}, objVal: ${objVal}, qnum: ${qnum}, isRedirUri: ${redir}")

	boolean result = false
	if(!getNestAuthToken()) { return result }

	try {
		if(getLastAnyCmdSentSeconds() > 120) {
			state.nestRedirectUrl = null
			state.remove("nestRedirectUrl")  // don't cache the redirect URL too long
		}

		def url = (!redir && state?.nestRedirectUrl) ? state?.nestRedirectUrl?.toString() : uri
		def data = new JsonBuilder("${obj}":objVal)
		def params = [
			uri: url,
			requestContentType: "application/json",
			headers: [
				"Content-Type": "application/json",
				"Authorization": "Bearer ${getNestAuthToken()}"
			],
			body: data.toString()
		]
		//def urlPath
		if((uri || state?.nestRedirectUrl) && !redir) {
			//urlPath = "/${type}/${typeId}"
			params["path"] = "/${type}/${typeId}"
		}

		LogTrace("${myStr} Url: $url | params: ${params}")
		LogAction("Processing Queued Cmd: [ObjId: ${typeId} | ObjType: ${type} | ObjKey: ${obj} | ObjVal: ${objVal} | QueueNum: ${qnum} | Redirect: ${redir}]", "trace", true)
		state?.lastCmdSent = "$type: (${objKey}: ${objVal})"

		adjThrottle(qnum, redir)

		def t0 = objVal
		if(t0 instanceof Map) { t0 = [:] + objVal }
		def asyncargs = [
			typeId: typeId,
			type: type,
			obj: obj,
			objVal: t0,
			qnum: qnum,
			cmd: cmd ]

		asynchttpPut('nestCmdResponse', params, asyncargs)

	} catch(ex) {
		log.error "${myStr} (command: $cmd) Exception:", ex
	}
}

def nestCmdResponse(resp, data) {
	LogAction("nestCmdResponse(${data?.cmd})", "info", false)
	def typeId = data?.typeId
	def type = data?.type
	def obj = data?.obj
	def objVal = data?.objVal
	if(objVal instanceof Map) { objVal = [:] + data?.objVal }
	def qnum = data?.qnum
	def command = data?.cmd
	def result = false
	try {
		if(!command) { cmdProcState(false); return }

		if(resp?.status == 307) {
			def redirUrl = resp?.headers?.Location
			def newUri = new URI(redirUrl?.toString())
			def newUrl = "${newUri?.getScheme()}://${newUri?.getHost()}:${newUri?.getPort()}"
			if((newUrl != null && newUrl.startsWith("https://")) && (!state?.nestRedirectUrl || state?.nestRedirectUrl != newUrl)) {
				state?.nestRedirectUrl = newUrl
				queueProcNestCmd(redirUrl, typeId, type, obj, objVal, qnum, command, true)
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
			LogAction("nestCmdResponse | Processed Queue: ${qnum} | Obj: ($type{$obj:$objVal}) SUCCESSFULLY!", "info", true)
			apiIssueEvent(false)
			state?.lastCmdSentStatus = "ok"
			//atomicState?.apiRateLimited = false
			//atomicState?.apiCmdFailData = null
			result = true
		}
/*
		if(resp?.status == 429) {
			// requeue command
			def newCmd = [command[0], command[1], command[2], command[3], command[4]]
			def tempQueue = []
			tempQueue << newCmd
			if(!atomicState?."cmdQ${qnum}" ) { atomicState."cmdQ${qnum}" = [] }
			def cmdQueue = atomicState?."cmdQ${qnum}"
			cmdQueue.each { cmd ->
				newCmd = [cmd[0], cmd[1], cmd[2], cmd[3], cmd[4]]
				tempQueue << newCmd
			}
			atomicState."cmdQ${qnum}" = tempQueue
		}
*/
		if(resp?.status != 200) {
			state?.lastCmdSentStatus = "failed"
			state.remove("nestRedirectUrl")
			if(resp?.hasError()) {
				apiRespHandler((resp?.getStatus() ?: null), (resp?.getErrorJson() ?: null), "nestCmdResponse", "nestCmdResponse ${qnum} ($type{$obj:$objVal})", true)
				//apiRespHandler(resp?.status, resp?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
			} else {
				LogAction("nestResponse could not process error ${qnum} ($type{$obj:$objVal})", "error", true)
			}
			apiIssueEvent(true)
/*
			atomicState?.lastCmdSentStatus = "failed"
			if(resp?.hasError()) {
				apiRespHandler((resp?.getStatus() ?: null), (resp?.getErrorJson() ?: null), "nestCmdResponse", "nestCmdResponse ${qnum} ($type{$obj:$objVal})", true)
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
		state?.lastCmdSentStatus = "failed"
		state.remove("nestRedirectUrl")
		cmdProcState(false)
		if(resp?.hasError()) {
			apiRespHandler((resp?.getStatus() ?: null), (/*resp?.getErrorJson() ?:*/ null), "nestCmdResponse Exception", "nestCmdResponse ${qnum} ($type{$obj:$objVal})", true)
		}
		apiIssueEvent(true)
		log.error "nestCmdResponse (command: $command) Exception:"//, ex
	}
}

/*

def procNestCmd(uri, typeId, type, objKey, objVal, qnum, redir = false) {
	def result = false
	if(!getNestAuthToken()) { return result }
	try {
		if(getLastAnyCmdSentSeconds() > 120) {
			state.nestRedirectUrl = null
			state.remove("nestRedirectUrl")  // don't cache the redirect URL too long
		}

		def url = (!redir && state?.nestRedirectUrl) ? state?.nestRedirectUrl?.toString() : uri
		def data = new JsonBuilder([(objKey):objVal])
		def params = [
				uri: url,
				contentType: "application/json",
				headers: [
						"Authorization": "Bearer ${getNestAuthToken()}"
				],
				body: data?.toString()
		]
		if((uri || state?.nestRedirectUrl) && !redir) {
			params["path"] = "/${type}/${typeId}"
		}
		state?.lastCmdSent = "$type: (${objKey}: ${objVal})"

		adjThrottle(qnum, redir)

		// LogTrace("procNestCmd time update recentSendCmd:  ${getRecentSendCmd(qnum)}  last seconds:${getLastCmdSentSeconds(qnum)} queue: ${qnum}")

		httpPut(params) { resp ->
			if(resp?.status == 307) {
				def redirUrl = resp?.headers?.location
				def newUri = new URI(redirUrl?.toString())
				def newUrl = "${newUri?.getScheme()}://${newUri?.getHost()}:${newUri?.getPort()}"
				if((newUrl != null && newUrl.startsWith("https://")) && (!state?.nestRedirectUrl || state?.nestRedirectUrl != newUrl)) {
					state?.nestRedirectUrl = newUrl
					if( procNestCmd(redirUrl, typeId, type, objKey, objVal, qnum, true) ) {
						return true
					}
				}
			}
			else if(resp?.status == 200) {
				LogAction("procNestCmd Processed Queue(${qnum}) Item: ($type{$objKey:$objVal}) SUCCESSFULLY!", "info", true)
				apiIssueEvent(false)
				state?.lastCmdSentStatus = "ok"
				//state?.apiRateLimited = false
				//state?.apiCmdFailData = null
				result = true
			}
			else {
				state?.lastCmdSentStatus = "failed"
				state.remove("nestRedirectUrl")
				apiRespHandler(resp?.status, resp?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
				apiIssueEvent(true)
			}
		}
	} catch (ex) {
		state?.lastCmdSentStatus = "failed"
		state.remove("nestRedirectUrl")
		cmdProcState(false)
		if (ex instanceof groovyx.net.http.HttpResponseException && ex?.response) {
			apiRespHandler(ex?.response?.status, ex?.response?.data, "procNestCmd", "procNestCmd ${qnum} ($type{$objKey:$objVal})", true)
		} else {
			log.error "procNestCmd Exception: ($type | $objKey:$objVal) | Message: ${ex?.message}"
		}
		apiIssueEvent(true)
	}
	return result
}
*/

void adjThrottle(qnum, redir) {
	if(!redir) {
		int t0 = getRecentSendCmd(qnum)
		int val = t0
		if(t0 > 0 /* && (getLastCmdSentSeconds(qnum) < 60) */ ) {
			val -= 1
		}
		int t1 = getLastCmdSentSeconds(qnum)
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

void apiRespHandler(code, errJson, methodName, tstr=null, isCmd=false) {
	// LogAction("[$methodName] | Status: (${code}) | Error Message: ${errJson}", "warn", true)
	if (!(code?.toInteger() in [200, 307])) {
		String result = ""
		boolean notif = true
		def errMsg = errJson?.message != null ? errJson?.message : null
		switch(code) {
			case 400:
				result = !errMsg ? "A Bad Request was made to the API..." : errMsg
				break
			case 401:
				result =  !errMsg ? "Authentication ERROR, Please try refreshing your login under Authentication settings..." : errMsg
				revokeNestToken()
				break
			case 403:
				result =  !errMsg ? "Forbidden: Your Login Credentials are Invalid..." : errMsg
				revokeNestToken()
				break
			case 429:
				result =  !errMsg ? "Requests are currently being blocked because of API Rate Limiting..." : errMsg
				state?.apiRateLimited = true
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
		state?.apiCmdFailData = failData
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

String getStrucVal(svariable) {
	def sData = state.structData
	def sKey = state?.structures
	def asStruc = sData && sKey && sData[sKey] ? sData[sKey] : null
	def retVal = asStruc ? asStruc[svariable] ?: null : null
	return (retVal != null) ? retVal as String : null
}

String getStZipCode() { return location?.zipCode?.toString() }

int getChildWaitVal() { return tempChgWaitVal() }

/************************************************************************************
 |	This Section Discovers all structures and devices on your Nest Account.			|
 |	It also Adds Removes Devices from Hubitat										|
 ************************************************************************************/

private Map getNestStructures() {
	//LogTrace("Getting Nest Structures")
	def struct = [:]
	def thisstruct = [:]
	try {
		if(ok2PollStruct()) { getApiData("str") }
		if(state.structData) {
			def structs = state.structData
			structs?.eachWithIndex { struc, index ->
				def strucId = struc?.key
				def strucData = struc?.value

				def dni = [strucData?.structure_id].join('.')
				struct[dni] = strucData?.name.toString()

				if(strucData?.structure_id.toString() == settings?.structures.toString()) {
					thisstruct[dni] = strucData?.name.toString()
				} else {
					if(state?.structures) {
						if(strucData?.structure_id?.toString() == state?.structures?.toString()) {
							thisstruct[dni] = strucData?.name.toString()
						}
					} else {
						if(!settings?.structures) {
							thisstruct[dni] = strucData?.name.toString()
						}
					}
				}
			}
			if(state?.thermostats || state?.protects || state?.cameras || state?.presDevice || state?.vThermostats) {   // if devices are configured, you cannot change the structure until they are removed
				struct = thisstruct
			}
			if(ok2PollDevice()) { getApiData("dev") }
		} else { LogAction("Missing: structData  ${state.structData}", "warn", true) }

	} catch (ex) {
		log.error "getNestStructures Exception: ${ex?.message}"
	}
	return struct
}

private Map getNestThermostats() {
	//LogTrace("Getting Thermostat list")
	def stats = [:]
	def tstats = deviceDataFLD?.thermostats
	//LogTrace("Found ${tstats?.size()} Thermostats")
	tstats.each { stat ->
		def statId = stat?.key
		def statData = stat?.value

		def adni = [statData?.device_id].join('.')
		if(statData?.structure_id == settings?.structures) {
			stats[adni] = getThermostatDisplayName(statData)
		}
	}
	return stats
}

private Map getNestProtects() {
	//LogTrace("Getting Nest Protect List")
	def protects = [:]
	def nProtects = deviceDataFLD?.smoke_co_alarms
	//LogTrace("Found ${nProtects?.size()} Nest Protects")
	nProtects.each { dev ->
		def devId = dev?.key
		def devData = dev?.value

		def bdni = [devData?.device_id].join('.')
		if(devData?.structure_id == settings?.structures) {
			protects[bdni] = getProtectDisplayName(devData)
		}
	}
	return protects
}

private Map getNestCameras() {
	//LogTrace("Getting Nest Camera List")
	def cameras = [:]
	def nCameras = deviceDataFLD?.cameras
	//LogTrace("Found ${nCameras?.size()} Nest Cameras")
	nCameras.each { dev ->
		def devId = dev?.key
		def devData = dev?.value

		def bdni = [devData?.device_id].join('.')
		if(devData?.structure_id == settings?.structures) {
			cameras[bdni] = getCameraDisplayName(devData)
		}
	}
	return cameras
}

private Map statState(val) {
	def stats = [:]
	def tstats = getNestThermostats()
	tstats.each { stat ->
		def statId = stat?.key
		def statData = stat?.value
		val.each { st ->
			if(statId == st) {
				def adni = [statId].join('.')
				stats[adni] = statData
			}
		}
	}
	return stats
}

private Map coState(val) {
	def protects = [:]
	def nProtects = getNestProtects()
	nProtects.each { dev ->
		val.each { pt ->
			if(dev?.key == pt) {
				def bdni = [dev?.key].join('.')
				protects[bdni] = dev?.value
			}
		}
	}
	return protects
}

private Map camState(val) {
	def cams = [:]
	def nCameras = getNestCameras()
	nCameras.each { dev ->
		val.each { cm ->
			if(dev?.key == cm) {
				def bdni = [dev?.key].join('.')
				cams[bdni] = dev?.value
			}
		}
	}
	return cams
}

String getThermostatDisplayName(stat) {
	if(stat?.name) { return stat.name.toString() }
	else if(stat?.name_long) { return stat?.name_long.toString() }
	else { return "Thermostatnamenotfound" }
}

String getProtectDisplayName(prot) {
	if(prot?.name) { return prot.name.toString() }
	else if(prot?.name_long) { return prot?.name_long.toString() }
	else { return "Protectnamenotfound" }
}

String getCameraDisplayName(cam) {
	if(cam?.name) { return cam.name.toString() }
	else if(cam?.name_long) { return cam?.name_long.toString() }
	else { return "Cameranamenotfound" }
}

String getNestDeviceDni(dni, type) {
	//LogTrace("getNestDeviceDni: $dni | $type")
	String retVal = ""
	def d1 = getChildDevice(dni?.key.toString())
	if(d1) { retVal = dni?.key.toString() }
	else {
		def t0 = "Nest${type}-${dni?.value.toString()} | ${dni?.key.toString()}"
		d1 = getChildDevice(t0)
		if(d1) { retVal = t0.toString() }
		retVal =  dni?.key.toString()
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
		if(state?.structures) {
			dni = "NestPres${state?.structures}" // old name 2
			d3 = getChildDevice(dni)
			if(d3) { return dni }
		}
		String retVal = ""
		if(state?.structures) { retVal = "NestPres | ${state?.structures}" }
		else if(settings?.structures) { retVal = "NestPres | ${settings?.structures}" }
		else {
			LogAction("getNestPresID No structures ${state?.structures}", "warn", true)
			return ""
		}
		return retVal
	}
}

String getDefaultLabel(String ttype, String name) {
	//LogTrace("getDefaultLabel: ${ttype} ${name}")
	if(name == null || name == "") {
		LogAction("BAD CALL getDefaultLabel: ${ttype}, ${name}", "error", true)
		return ""
	}
	String defName
	switch (ttype) {
		case "thermostat":
			defName = "Nest Thermostat - ${name}"
			if(state?.devNameOverride && state?.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "protect":
			defName = "Nest Protect - ${name}"
			if(state?.devNameOverride && state?.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "camera":
			defName = "Nest Camera - ${name}"
			if(state?.devNameOverride && state?.useAltNames) { defName = "${location.name} - ${name}" }
			break
		case "vthermostat":
			defName = "Nest vThermostat - ${name}"
			if(state?.devNameOverride && state?.useAltNames) { defName = "${location.name} - Virtual ${name}" }
			break
		case "presence":
			defName = "Nest Presence Device"
			if(state?.devNameOverride && state?.useAltNames) { defName = "${location.name} - Nest Presence Device" }
			break
		default:
			LogAction("BAD CALL getDefaultLabel: ${ttype}, ${name}", "error", true)
			return ""
			break
	}
	return defName
}

String getNestTstatLabel(name, key) {
	//LogTrace("getNestTstatLabel: ${name}")
	String defName = getDefaultLabel("thermostat", name)
	if(state?.devNameOverride && state?.custLabelUsed) {
		return settings?."tstat_${key}_lbl" ?: defName
	}
	return defName
}

String getNestProtLabel(name, key) {
	String defName = getDefaultLabel("protect", name)
	if(state?.devNameOverride && state?.custLabelUsed) {
		return settings?."prot_${key}_lbl" ?: defName
	}
	return defName
}

String getNestCamLabel(name, key) {
	String defName = getDefaultLabel("camera", name)
	if(state?.devNameOverride && state?.custLabelUsed) {
		return settings?."cam_${key}_lbl" ?: defName
	}
	return defName
}

String getNestVtstatLabel(name, key) {
	String defName = getDefaultLabel("vthermostat", name)
	if(state?.devNameOverride && state?.custLabelUsed) {
		return settings?."vtstat_${key}_lbl" ?: defName
	}
	return defName
}

String getNestPresLabel() {
	String defName = getDefaultLabel("presence", "name")
	if(state?.devNameOverride && state?.custLabelUsed) {
		return settings?.presDev_lbl ? settings?.presDev_lbl.toString() : defName
	}
	return defName
}

String getChildDeviceLabel(dni) {
	if(!dni) { return null }
	def t0 = getChildDevice(dni.toString())
	return t0?.getLabel() ?: null
}

def addRemoveDevices(uninst = null) {
	LogTrace("addRemoveDevices")
	def retVal = false
	try {
		def devsInUse = []
		def tstats
		def nProtects
		def nCameras
		def nVstats
		int devsCrt = 0
		int presCnt = 0
		def streamCnt = 0
		boolean noCreates = true
		boolean noDeletes = true

		if(!uninst) {
			if(state?.thermostats) {
				tstats = state?.thermostats?.collect { dni ->
					def d1 = getChildDevice(getNestTstatDni(dni))
					if(!d1) {
						String d1Label = getNestTstatLabel("${dni?.value}", "${dni.key}")
						d1 = addChildDevice(namespace(), getThermostatChildName(), dni?.key, null, [label: "${d1Label}"])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d1?.displayName} with (Id: ${dni?.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d1?.displayName} with (Id: ${dni?.key})", "debug", true)
					}
					devsInUse += dni.key
				}
			}

			if(state?.protects) {
				nProtects = state?.protects?.collect { dni ->
					def d2 = getChildDevice(getNestProtDni(dni).toString())
					if(!d2) {
						String d2Label = getNestProtLabel("${dni.value}", "${dni.key}")
						d2 = addChildDevice(namespace(), getProtectChildName(), dni.key, null, [label: "${d2Label}"])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d2?.displayName} with (Id: ${dni?.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d2?.displayName} with (Id: ${dni?.key})", "debug", true)
					}
					devsInUse += dni.key
				}
			}

			if(state?.presDevice) {
				try {
					String dni = getNestPresId()
					def d3 = getChildDevice(dni)
					if(!d3) {
						String d3Label = getNestPresLabel()
						d3 = addChildDevice(namespace(), getPresenceChildName(), dni, null, [label: "${d3Label}"])
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

			if(state?.cameras) {
				nCameras = state?.cameras?.collect { dni ->
					def d4 = getChildDevice(getNestCamDni(dni).toString())
					if(!d4) {
						String d4Label = getNestCamLabel("${dni.value}", "${dni.key}")
						d4 = addChildDevice(namespace(), getCameraChildName(), dni.key, null, [label: "${d4Label}"])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d4?.displayName} with (Id: ${dni?.key})", "debug", true)
					} else {
						LogAction("Found Existing Device: ${d4?.displayName} with (Id: ${dni?.key})", "debug", true)
					}
					devsInUse += dni.key
				}
			}

			if(state?.vThermostats) {
				nVstats = state?.vThermostats.collect { dni ->
					//LogAction("state.vThermostats: ${state.vThermostats} dni: ${dni} dni.key: ${dni.key.toString()} dni.value: ${dni.value.toString()}", "debug", true)
					def d6 = getChildDevice(getNestvStatDni(dni).toString())
					if(!d6) {
						String d6Label = getNestVtstatLabel("${dni.value}", "${dni.key}")
						//LogAction("CREATED: ${d6Label} with (Id: ${dni.key})", "debug", true)
						d6 = addChildDevice(namespace(), getThermostatChildName(), dni.key, null, [label: "${d6Label}", "data":["isVirtual":"true"]])
						devsCrt = devsCrt + 1
						LogAction("Created: ${d6?.displayName} with (Id: ${dni?.key})", "debug", true)
					} else {
						LogAction("Found: ${d6?.displayName} with (Id: ${dni?.key}) exists", "debug", true)
					}
					devsInUse += dni.key
					return d6
				}
			}


			if(restEnabled()) {
				def d5 = getChildDevice(getEventDeviceDni())
				if(!d5) {
					d5 = addChildDevice(namespace(), getEventDeviceName(), getEventDeviceDni(), null, [label: getEventDeviceName()])
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
				updTimestampMap("lastAnalyticUpdDt", null)
			}
		}

		if(uninst) {
			state?.thermostats = []
			state?.vThermostats = []
			state?.protects = []
			state?.cameras = []
			state?.presDevice = false
			state?.streamDevice = false
		}

		def noDeleteErr = true
		def toDelete
		LogTrace("addRemoveDevices devicesInUse: ${devsInUse}")
		toDelete = getChildDevices().findAll { !devsInUse?.toString()?.contains(it?.deviceNetworkId) }

		if(toDelete?.size() > 0) {
			// log.debug "delete: $delete"
			// log.debug "devsInUse: $devsInUse"
			noDeletes = false
			noDeleteErr = false
			updTimestampMap("lastAnalyticUpdDt", null)
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

def addRemoveVthermostat(tstatdni, tval, myID) {
	def odevId = tstatdni
	LogAction("addRemoveVthermostat() tstat: ${tstatdni}  devid: ${odevId}  tval: ${tval}  myID: ${myID} vThermostats: ${state?.vThermostats} ", "trace", true)

	if(parent || !myID || tval == null) {
		LogAction("got called BADLY ${parent}  ${myID}  ${tval}", "warn", true)
		return false
	}
	def tstat = tstatdni
	def tStatPhys

	def d1 = getChildDevice(odevId.toString())
	if(!d1) {
		LogAction("addRemoveVthermostat: Cannot find thermostat device child", "error", true)
		if(tval) { return false }  // if deleting (false), let it try to proceed
	} else {
		tstat = d1
		tStatPhys = tstat?.currentNestType == "physical" ? true : false
		if(!tStatPhys && tval) { LogAction("addRemoveVthermostat: Cannot create a virtual thermostat on a virtual thermostat device child", "error", true) }
	}

	def devId = "v${odevId}"

	// def migrate = migrationInProgress()

	// if(!migrate && state?."vThermostat${devId}" && myID != state?."vThermostatChildAppId${devId}") {
	if(state?."vThermostat${devId}" && myID != state?."vThermostatChildAppId${devId}") {
		LogAction("addRemoveVthermostat() not ours ${myID} ${state?."vThermostat${devId}"} ${state?."vThermostatChildAppId${devId}"}", "trace", true)
		//state?."vThermostat${devId}" = false
		//state?."vThermostatChildAppId${devId}" = null
		//state?."vThermostatMirrorId${devId}" = null
		//state?.vThermostats = null
		return false

	} else if(tval && state?."vThermostat${devId}" && myID == state?."vThermostatChildAppId${devId}") {
		LogAction("addRemoveVthermostat() already created ${myID} ${state?."vThermostat${devId}"} ${state?."vThermostatChildAppId${devId}"}", "trace", true)
		return true

	} else if(!tval && !state?."vThermostat${devId}") {
		LogAction("addRemoveVthermostat() already removed ${myID} ${state?."vThermostat${devId}"} ${state?."vThermostatChildAppId${devId}"}", "trace", true)
		return true

	} else {
		state."vThermostat${devId}" = tval
		if(tval && !state?."vThermostatChildAppId${devId}") {
			LogAction("addRemoveVthermostat() marking for create virtual thermostat tracking ${tstat}", "trace", true)
			state."vThermostatChildAppId${devId}" = myID
			state?."vThermostatMirrorId${devId}" = odevId
			def vtlist = state?.vThermostats ?: [:]
			vtlist[devId] = "${tstat.label.toString()}"
			state.vThermostats = vtlist
			if(!settings?.resetAllData) { runIn(120, "updated", [overwrite: true]) }  // create what is needed

		} else if(!tval && state?."vThermostatChildAppId${devId}") {
			LogAction("addRemoveVthermostat() marking for remove virtual thermostat tracking ${tstat}", "trace", true)
			state."vThermostatChildAppId${devId}" = null
			state?."vThermostatMirrorId${devId}" = null

			state.remove("vThermostat${devId}" as String)
			state.remove("vThermostatChildAppId${devId}" as String)
			state.remove("vThermostatMirrorId${devId}" as String)
			state.remove("oldvstatData${devId}" as String)

			def vtlist = state?.vThermostats
			def newlist = [:]
			def vtstat
			vtstat = vtlist.collect { dni ->
				//LogAction("vThermostats: ${state.vThermostats}  dni: ${dni}  dni.key: ${dni.key.toString()}  dni.value: ${dni.value.toString()} devId: ${devId}", "debug", true)
				def ttkey = dni.key.toString()
				if(ttkey == devId) { ; /*log.trace "skipping $dni"*/ }
				else { newlist[ttkey] = dni.value }
				return true
			}
			vtlist = newlist
			state.vThermostats = vtlist
			if(!settings?.resetAllData) { runIn(120, "updated", [overwrite: true]) }  // create what is needed
		} else {
			LogAction("addRemoveVthermostat() unexpected operation state ${myID} ${state?."vThermostat${devId}"} ${state?."vThermostatChildAppId${devId}"}", "warn", true)
			return false
		}
		return true
	}
}


boolean getAccessToken() {
	if(!state.access_token) {
		try {
			state.access_token = createAccessToken()
		}
		catch (ex) {
			String msg = "Error: OAuth is not Enabled for ${app?.name}!.  Please click remove and Enable Oauth under the App Settings in the IDE"
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
	//state?.restStreamingOn = false
	revokeAccessToken()
	state?.access_token = null
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

boolean nestDevAccountCheckOk() {
	if(getNestAuthToken() == null && (clientId() == null || clientSecret() == null) ) { return false }
	else { return true }
}

Map devClientData() {
	int clt = devCltNum() ?: 1
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
	if(settings?.useMyClientId && settings?.clientId) { return settings?.clientId }
	return devClientData()?.id ?: null//Developer ID
}

String clientSecret() {
	if(settings?.useMyClientId && settings?.clientSecret) { return settings?.clientSecret }
	return devClientData()?.secret ?: null//Developer Secret
}

String getNestAuthToken() { return (state?.authData && state?.authData?.token) ? state?.authData?.token : null }

String getOauthInitUrl() {
	def oauthParams = [
		response_type: "code",
		client_id: clientId(),
		state: getOauthState(),
		redirect_uri: getCallbackUrl()
	]
//Logger("getOauthInitUrl:  https://home.nest.com/login/oauth2?${toQueryString(oauthParams)}", "error")
	return "https://home.nest.com/login/oauth2?${toQueryString(oauthParams)}"
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
			def tokenParams = [
					code: code.toString(),
					client_id: clientId(),
					client_secret: clientSecret(),
					grant_type: "authorization_code",
			]
			def tokenUrl = "https://api.home.nest.com/oauth2/access_token?${toQueryString(tokenParams)}"
//Logger("callback: https://api.home.nest.com/oauth2/access_token?${toQueryString(tokenParams)}", "error")
			httpPost(uri: tokenUrl) { resp ->
				Map authData = [:]
				authData["token"] = resp?.data.access_token
				if(authData?.token) {
					updTimestampMap("authTokenCreatedDt", getDtNow())
					authData["tokenExpires"] = resp?.data.expires_in
					state.authData = authData
				}
			}
			if(state?.authData?.token) {
				LogAction("Nest AuthToken Generated SUCCESSFULLY", "info", true)
				if(state?.isInstalled) {
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
	LogTrace("finishRemap (${state?.pollBlocked}) (${state?.pollBlockedReason})")
	fixDevAS()
	state.pollBlocked = false
	state.pollBlockedReason = ""
	state.needToFinalize = false
	initialize()
}

String getNestApiUrl()	{ return "https://developer-api.nest.com" }
def getStructure()	{ return state?.structures ?: null }

String getCallbackUrl(){ return "https://cloud.hubitat.com/api/nest" }
String getOauthState()	{ return "${getHubUID()}/apps/${app?.id}/callback?access_token=${state?.access_token}" }
String getAppEndpointUrl(subPath)	{ return "${getApiServerUrl()}/${getHubUID()}/apps/${app?.id}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }
String getLocalEndpointUrl(subPath){ return "${getLocalApiServerUrl()}/apps/${app?.id}${subPath ? "/${subPath}" : ""}?access_token=${state?.access_token}" }
String sectionTitleStr(String title)	{ return "<h3>$title</h3>" }
String inputTitleStr(String title)	{ return "<u>$title</u>" }
String pageTitleStr(String title)	{ return "<h1>$title</h1>" }
String paraTitleStr(String title)	{ return "<b>$title</b>" }

String imgTitle(String imgSrc, String titleStr, String color=null, imgWidth=30, imgHeight=null) {
	String imgStyle = ""
	imgStyle += imgWidth ? "width: ${imgWidth}px !important;" : ""
	imgStyle += imgHeight ? "${imgWidth ? " " : ""}height: ${imgHeight}px !important;" : ""
	if(color) { return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""" }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""" }
}

void revokeNestToken() {
	if(getNestAuthToken()) {
		LogAction("revokeNestToken()", "info", true)
		restStreamHandler(true, "revokeNestToken()", false)
		state?.restStreamingOn = false
		def params = [
				uri: "https://api.home.nest.com",
				path: "/oauth2/access_tokens/${getNestAuthToken()}",
				contentType: 'application/json'
		]
		try {
			httpDelete(params) { resp ->
				if(resp?.status == 204) {
					LogAction("Nest Token revoked", "warn", true)
					revokeCleanState()
					return //true
				}
			}
		}
		catch (ex) {
			if(ex?.message?.toString() == "Not Found") {
				revokeCleanState()
				return //true
			} else {
				log.error "revokeNestToken Exception: ${ex?.message}"
				revokeCleanState()
				return //false
			}
		}
	} else { revokeCleanState() }
}

void revokeCleanState() {
LogTrace("revokeCleanState")
	unschedule()
	atomicState?.diagRunInOn = false
	state?.access_token = null
	state?.accessToken = null
	state?.authData = null
	updTimestampMap("authTokenCreatedDt", null)
	//state?.nestAuthTokenExpires = getDtNow()
	state.structData = null
	deviceDataFLD = null
	state.metaData = null
	updTimestampMap("lastStrDataUpd", null)
	updTimestampMap("lastDevDataUpd", null)
	updTimestampMap("lastMetaDataUpd", null)
	resetPolling()
	state?.pollingOn = false
	state?.streamPolling = false
	state.pollBlocked = true
	state.pollBlockedReason = "No Auth Token"
	atomicState.workQrunInActive = false
}

//HTML Connections Pages
def success() {
	def message = """
	<p>Your Hubitat Elevation is now connected to Nest!</p>
	<p>You will be redirected back to the Hubitat App to finish the rest of the setup in a couple seconds.</p>
	"""
	connectionStatus(message, true)
}

def fail() {
	def message = """
	<p>The connection could not be established!</p>
	<p>You will be redirected back to the Hubitat App to try the connection again.</p>
	"""
	connectionStatus(message, true)
}

def connectionStatus(message, close = false) {
	def redirectHtml = close ? """<script>document.getElementsByTagName('html')[0].style.cursor = 'wait';setTimeout(function(){window.close()},2500);</script>""" : ""
	def html = """
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
				<img src="https://pbs.twimg.com/profile_images/519883786020847617/TqhjjrE__400x400.png" alt="nest icon" width="120px"/>
				${message}
			</div>
		${redirectHtml}
		</body>
		</html>
		"""
/* """ */
	render contentType: 'text/html', data: html
}

def toJson(Map m) {
	return new org.json.JSONObject(m).toString()
}

def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

/************************************************************************************************
 |									LOGGING AND Diagnostic										|
 *************************************************************************************************/
void LogTrace(String msg, String logSrc=(String)null) {
	boolean trOn = (settings?.appDebug && settings?.advAppDebug && !settings?.enRemDiagLogging) ? true : false
	if(trOn) {
		boolean logOn = (settings?.enRemDiagLogging && state?.enRemDiagLogging) ? true : false
		//def theLogSrc = (logSrc == null) ? (parent ? "Automation" : "Manager") : logSrc
		Logger(msg, "trace", logSrc, logOn)
	}
}

void LogAction(String msg, String type="debug", boolean showAlways=false, String logSrc=null) {
	boolean isDbg = (settings?.appDebug /* && !enRemDiagLogging */) ? true : false
	//def theLogSrc = (logSrc == null) ? (parent ? "Automation" : "Manager") : logSrc
	if(showAlways || (isDbg && !showAlways)) { Logger(msg, type, logSrc) }

//	if (showAlways || isDbg) { Logger(msg, type) }
}

String tokenStrScrubber(String str) {
	def regex1 = /(Bearer c.{1}\w+)/
	def regex2 = /(auth=c.{1}\w+)/
	String newStr = str.replaceAll(regex1, "Bearer 'token code redacted'")
	newStr = newStr.replaceAll(regex2, "auth='token code redacted'")
	//log.debug "newStr: $newStr"
	return newStr
}

void Logger(String msg, String type, String logSrc=(String)null, boolean noSTlogger=false) {
	String labelstr = ""
	boolean logOut = true
	if(settings?.dbgAppndName == true) { labelstr = "${app.label} | " }
	if(msg && type) {
		String themsg = tokenStrScrubber("${labelstr}${msg}")
		if(state?.enRemDiagLogging && settings?.enRemDiagLogging && state?.remDiagAppAvailable == true) {
			String theLogSrc = (logSrc == null) ? (parent ? "Automation" : "Manager") : logSrc
			if(saveLogtoRemDiagStore(themsg, type, theLogSrc) == true) {
				logOut = false
			}
		}
		if(logOut == true) {
		switch(type) {
			case "debug":
				log.debug "${themsg}"
				break
			case "info":
				log.info " ${themsg}"
				break
			case "trace":
				log.trace "${themsg}"
				break
			case "error":
				log.error "${themsg}"
				break
			case "warn":
				log.warn "${themsg}"
				break
			default:
				log.debug "${themsg}"
				break
		}
		}
	}
	else { log.error "${labelstr}Logger Error - type: ${type} | msg: ${msg}" }
}

def getDiagLogTimeRemaining() {
	return sec2PrettyTime((3600*48) - Math.abs((getRemDiagActSec() ?: 0)))
}

String sec2PrettyTime(Integer timeSec) {
    Integer years = Math.floor(timeSec / 31536000); timeSec -= years * 31536000;
    Integer months = Math.floor(timeSec / 31536000); timeSec -= months * 2592000;
    Integer days = Math.floor(timeSec / 86400); timeSec -= days * 86400;
    Integer hours = Math.floor(timeSec / 3600); timeSec -= hours * 3600;
    Integer minutes = Math.floor(timeSec / 60); timeSec -= minutes * 60;
    Integer seconds = Integer.parseInt((timeSec % 60) as String, 10);
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

def saveLogtoRemDiagStore(String msg, String type, String logSrcType=null, frc=false) {
	def retVal = false
	// log.trace "saveLogtoRemDiagStore($msg, $type, $logSrcType)"
	if(state?.enRemDiagLogging && settings?.enRemDiagLogging) {
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
				def dt = new Date().getTime()
				def item = ["dt":dt, "type":type, "src":(logSrcType ?: "Not Set"), "msg":msg]
				def data = atomicState?.remDiagLogDataStore ?: []
				data << item
				atomicState?.remDiagLogDataStore = data
				retVal = true
			}
		}

		if(frc) {
			def data = atomicState?.remDiagLogDataStore ?: []
			def t0 = data?.size()
			if(t0) {
				diagLogChecks(frc)
			}
		} else {
			if(!atomicState?.diagRunInOn) {
				atomicState?.diagRunInOn = true
				runIn(10, "diagLogChecks", [overwrite: true])
			}
		}
/*
			def data = atomicState?.remDiagLogDataStore ?: []
			def t0 = data?.size()
			if(t0 && (t0 > 30 || frc || getLastRemDiagSentSec() > 120 || getStateSizePerc() >= 65)) {
				def remDiagApp = getRemDiagApp()
				if(remDiagApp) {
					remDiagApp?.savetoRemDiagChild(data)
					updTimestampMap("remDiagDataSentDt", getDtNow())
				} else {
					log.warn "Remote Diagnostics Child app not found"
					if(getRemDiagActSec() > 20) {   // avoid race that child did not start yet
						diagLogProcChange(false)
					}
					retVal = false
				}
				atomicState?.remDiagLogDataStore = []
			}
		}
*/
	}
	return retVal
}

void diagLogChecks(frc=false) {
	atomicState?.diagRunInOn = false
	if(state?.enRemDiagLogging && settings?.enRemDiagLogging) {
		boolean turnOff = false
		String reasonStr = ""
		if(!frc && getRemDiagActSec() > (3600 * 48)) {
			turnOff = true
			reasonStr += "was active for last 48 hours "
			saveLogtoRemDiagStore("Diagnostics disabled due to ${reasonStr}", "info", "Manager", true)
			diagLogProcChange(false)
			log.info "Remote Diagnostics disabled ${reasonStr}"
			return
		}
		def data = atomicState?.remDiagLogDataStore ?: []
		def t0 = data?.size()
		if(t0 && (t0 > 30 || frc || getLastRemDiagSentSec() > 120 || getStateSizePerc() >= 65)) {
			def remDiagApp = getRemDiagApp()
			if(remDiagApp) {
				remDiagApp?.savetoRemDiagChild(data)
				atomicState?.remDiagLogDataStore = []
				updTimestampMap("remDiagDataSentDt", getDtNow())
			} else {
				log.warn "Remote Diagnostics Child app not found"
				if(getRemDiagActSec() > 20) {   // avoid race that child did not start yet
					diagLogProcChange(false)
				}
				retVal = false
			}
		}
	}
}

void settingUpdate(String name, value, Stringtype=null) {
	//LogAction("settingUpdate($name, $value, $type)...", "trace", false)
	if(name && type) {
		app?.updateSetting("$name", [type: "$type", value: value])
	}
	else if (name && type == null){ app?.updateSetting(name.toString(), value) }
}

void settingRemove(String name) {
	//LogAction("settingRemove($name)...", "trace", false)
	if(name) { app?.clearSetting("$name") }
}

def stateUpdate(String key, value) {
	if(key) { state?."${key}" = value }
	else { LogAction("stateUpdate: null key $key $value", "error", true) }
}

//Things that need to clear up on updates go here
void stateCleanup() {
	// LogAction("stateCleanup", "trace", true)
	def data = [ "deviceData", "cmdIsProc", "apiIssuesList", "cmdQlist", "nestRedirectUrl" /*, "timestampDtMap" , "accessToken" */ ]
	["lastCmdSent", "recentSendCmd", "cmdQ", "remSenLock", "oldTstat", "oldvstat", "oldCamData", "oldProt", "oldPres" ]?.each { oi->
		state?.each { if(it?.key?.toString().startsWith(oi)) { data?.push(it?.key) } }
	}
	data?.each { item ->
		state?.remove(item?.toString())
	}
	updTimestampMap("lastApiIssueMsgDt", null)
	atomicState.workQrunInActive = false
	state.forceChildUpd = true
	def sdata = ["updChildOnNewOnly"]
	sdata.each { item ->
		if(settings?."${item}" != null) {
			settingUpdate("${item.toString()}", "")	// clear settings
		}
	}
}

/******************************************************************************
 *								STATIC METHODS								*
 *******************************************************************************/
String getThermostatChildName()	{ return "Nest Thermostat" }
String getProtectChildName()	{ return "Nest Protect" }
String getPresenceChildName()	{ return "Nest Presence" }
String getCameraChildName()	{ return "Nest Camera" }
String getEventDeviceName()	{ return "Nest Eventstream" }
String getEventDeviceDni()	{ return "nest-eventstream01" }

String getAppImg(imgName, on = true)	{ return on ? "https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/App/$imgName" : "" }
String getDevImg(imgName, on = true)	{ return on ? "https://raw.githubusercontent.com/tonesto7/nest-manager/master/Images/Devices/$imgName" : "" }
/*
private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }
private String convertHexToIP(hex) { [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".") }
*/

//Returns app State Info
int getStateSize() {
	def resultJson = new groovy.json.JsonOutput().toJson(state)
	return resultJson?.toString().length()
}
int getStateSizePerc()  { return (int) ((stateSize / 100000)*100).toDouble().round(0) } //

String debugStatus() { return !settings?.appDebug ? "Off" : "On" }
boolean isAppDebug() { return !settings?.appDebug ? false : true }

String getObjType(obj, retType=false) {
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

String formatDt(dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("Hubitat TimeZone is not set; Please verify your Zip code is set under Hub Settings", "warn", true)
	}
	return tf.format(dt)
}

private int getTimeSeconds(String timeKey, int defVal, String meth) {
	def t0 = getTimestampVal(timeKey)
	return !t0 ? defVal : GetTimeDiffSeconds(t0, null, meth).toInteger()
}

String getTimestampVal(String val) {
	def tsData = state?.timestampDtMap
	if(val && tsData && tsData[val]) { return tsData[val] }
	return (String)null
}

void updTimestampMap(String keyName, dt=null) {
	def data = state?.timestampDtMap ?: [:]
	if(keyName) { data[keyName] = dt }
	state?.timestampDtMap = data
}

def GetTimeDiffSeconds(String strtDate, String stpDate=null, String methName=null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		def now = new Date()
		String stopVal = stpDate ? stpDate.toString() : formatDt(now)
		long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		long diff = (int) (long) (stop - start) / 1000 //
		//LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}

String getDtNow() {
	def now = new Date()
	return formatDt(now)
}

String strCapitalize(String str) {
	return str ? str?.toString().capitalize() : null
}

def getSettingVal(var) {
	if(var == null) { return settings }
	return settings[var] ?: null
}

def getStateVal(var) {
	return state[var] ?: null
}

// Calls by Automation children
// parent only method

boolean remSenLock(val, myId) {
	boolean res = false
	def k = "remSenLock${val}"
	if(val && myId) {
		def lval = state?."${k}"
		if(!lval) {
			state?."${k}" = myId
			res = true
		} else if(lval == myId) { res = true }
	}
	return res
}

boolean remSenUnlock(val, myId) {
	boolean res = false
	if(val && myId) {
		def k = "remSenLock${val}"
		def lval = state?."${k}"
		if(lval) {
			if(lval == myId) {
				state?."${k}" = null
				state.remove("${k}" as String)
				res = true
			}
		} else { res = true }
	}
	return res
}

boolean automationNestModeEnabled(val=null) {
	LogTrace("NestModeEnabled: $val")
	return getSetVal("automationNestModeEnabled", val)

/*
	if(val == null) {
		return state?.automationNestModeEnabled ?: false
	} else {
		state.automationNestModeEnabled = val.toBoolean()
	}
	return state?.automationNestModeEnabled ?: false
*/
}

boolean setNModeActive(val=null) {
	LogTrace("setNModeActive: $val")
	def myKey = "automationNestModeEcoActive"
	def retVal
	if(!automationNestModeEnabled(null)) {
		retVal = getSetVal(myKey, false)
/*
	if(automationNestModeEnabled(null)) {
		return getSetVal(myKey, val)
		if(val == null) {
			return state?.automationNestModeEcoActive ?: false
		} else {
			state.automationNestModeEcoActive = val.toBoolean()
		}

	} else { getSetVal(myKey, false) }
	//return state?.automationNestModeEcoActive ?: false
*/
	} else { retVal = getSetVal(myKey, val) }
	return retVal
}

boolean getSetVal(k, val=null) {
	if(val == null) {
		return state?."${k}" ?: false
	} else {
		state."${k}" = val.toBoolean()
	}
	return state?."${k}" ?: false
}

def getDevice(dni) {
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
