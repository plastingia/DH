/**
 *  Copyright 2016 CSC
 *  Do not re-distribute without agreement from the Author
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Smart Motion Sensor (HM)
 *	Date: 2016-10-08
 *	Version: 1.0
 */
metadata {
	definition (name: "HM Z-Wave Motion Sensor", namespace: "sc", author: "CSC") {
        capability "Motion Sensor"
		capability "Battery"
		capability "Configuration"

		attribute "sensor", "string"

		fingerprint deviceId: "0x1000", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x73, 0x80, 0x71, 0x85, 0x59, 0x84, 0x30", outClusters: ""
	}

	simulator {
		status "active": "command: 3003, payload: FF"
		status "inactive": "command: 3003, payload: 00"
	}

	tiles {
		standardTile("motion", "device.motion", width: 2, height: 2) {
            state "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
            state "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}

		main(["motion"])
		details(["motion", "battery"])
	}
}

//rawDescription: 	zw device: 24, command: 5E02, payload: 01 06 00 0C 07 0C 07

def parse(String description) {
	log.debug "description: $description"
    
	def result = []
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description, displayed:true)
	} else {
	  //def cmd = zwave.parse(description, [0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 1, 0x9C: 1])
	  //30 - Sensor Binary (motion), 71 - Alarm/Notification, 80 - Battery, 84 - Wake-up
        def cmd = zwave.parse(description, [0x30: 1, 0x71: 1, 0x80: 1, 0x84: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	return result
}

def parse_test(String description) {
	log.debug "description: $description"

    def result = null
    def cmd = zwave.parse(description)
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    return result
}

def updated() {
	response(zwave.wakeUpV1.wakeUpNoMoreInformation())
}

def sensorValueEvent(Short value) {
	log.debug "value: $value"
	if (value == 0) {
    	log.debug "motion is inactive"
		createEvent([ name: "motion", value: "inactive" ])
	} else if (value == 255) {
    	log.debug "motion is active1"
		createEvent([ name: "motion", value: "active" ])
	} else {
    	log.debug "motion is active2"
		[ createEvent([ name: "motion", value: "active" ]),
			createEvent([ name: "level", value: value ]) ]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{
	sensorValueEvent(cmd.alarmLevel)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [ displayed: true, value: cmd.scaledSensorValue.toString() ]
	switch (cmd.sensorType) {
		case 1:
			map.name = "temperature"
			map.unit = cmd.scale == 1 ? "F" : "C"
			break;
		case 2:
			map.name = "value"
			map.unit = cmd.scale == 1 ? "%" : ""
			break;
		case 3:
			map.name = "illuminance"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			break;
		case 4:
			// power
			map.name = "power"
			map.unit = cmd.scale == 1 ? "Btu/h" : "W"
			break;
		case 5:
			map.name = "humidity"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = cmd.scale == 0 ? "%" : ""
			break;
		case 6:
			map.name = "velocity"
			map.unit = cmd.scale == 1 ? "mph" : "m/s"
			break;
		case 8:
		case 9:
			map.name = "pressure"
			map.unit = cmd.scale == 1 ? "inHg" : "kPa"
			break;
		case 0xE:
			map.name = "weight"
			map.unit = cmd.scale == 1 ? "lbs" : "kg"
			break;
		case 0xF:
			map.name = "voltage"
			map.unit = cmd.scale == 1 ? "mV" : "V"
			break;
		case 0x10:
			map.name = "current"
			map.unit = cmd.scale == 1 ? "mA" : "A"
			break;
		case 0x12:
			map.name = "air flow"
			map.unit = cmd.scale == 1 ? "cfm" : "m^3/h"
			break;
		case 0x1E:
			map.name = "loudness"
			map.unit = cmd.scale == 1 ? "dBA" : "dB"
			break;
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	def result = []
	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
	result << createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
	} else {
		map.value = cmd.batteryLevel
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	def versions = [0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 1, 0x9C: 1]
	// def encapsulatedCommand = cmd.encapsulatedCommand(versions)
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	def event = [ displayed: false ]
	event.linkText = device.label ?: device.name
	event.descriptionText = "$event.linkText: $cmd"
	createEvent(event)
}


def configure() {
	zwave.wakeUpV1.wakeUpNoMoreInformation().format()
}
