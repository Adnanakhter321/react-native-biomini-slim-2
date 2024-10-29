import { NativeEventEmitter, NativeModules, ToastAndroid } from "react-native";

const { BioMiniModule } = NativeModules;
const eventEmitter = new NativeEventEmitter(BioMiniModule);

type getDeviceNameType = () => Promise<any>;
type initUsbListenerType = () => Promise<any>;
type connectBioMiniDeviceType = () => Promise<any>;
type initializeBioMiniDeviceType = () => Promise<any>;
type createBioMiniDeviceType = () => Promise<any>;
type removeDeviceType = () => Promise<any>;
type doSingleCaptureType = () => Promise<any>;
type doAbortCaptureType = () => Promise<any>;
type getCurrentDeviceNameType = () => Promise<any>;
type hasUSBOTGType = () => Promise<any>;


const getDeviceName: getDeviceNameType = () =>
  BioMiniModule.getDeviceName();
const isUsbDeviceConnected: getDeviceNameType = () =>
  BioMiniModule.isUsbDeviceConnected();
const initUsbListener: initUsbListenerType = () =>
  BioMiniModule.initUsbListener();
const requestPermissionUsb: connectBioMiniDeviceType = () =>
  BioMiniModule.requestPermissionUsb();
const initializeBioMiniDevice: initializeBioMiniDeviceType = () =>
  BioMiniModule.initUsbListener();
const createBioMiniDevice: createBioMiniDeviceType = () =>
  BioMiniModule.createBioMiniDevice();
const removeDevice: removeDeviceType = () =>
  BioMiniModule.removeDevice();
const doSingleCapture: doSingleCaptureType = () =>
  BioMiniModule.doSingleCapture();
const doAbortCapture: doAbortCaptureType = () =>
  BioMiniModule.abortCapturing();
const getCurrentDeviceName: getCurrentDeviceNameType = () =>
  BioMiniModule.getCurrentDeviceName();
const hasUSBOTG: hasUSBOTGType = () =>
  BioMiniModule.hasUsbHostFeature();
const usbConnectedListener  = (listener:any) =>
  eventEmitter.addListener('USBConnected', listener);
const usbDisconnectedListener = (listener:any) =>
  eventEmitter.addListener('USBDisconnected', listener);
const isUSBPermissionGranted = (listener:any) =>
  eventEmitter.addListener('USBPermissionGranted', listener);


export default {
  getDeviceName,
  initUsbListener,
  isUsbDeviceConnected,
  requestPermissionUsb,
  initializeBioMiniDevice,
  createBioMiniDevice,
  removeDevice,
  doSingleCapture,
  usbConnectedListener,
  usbDisconnectedListener,
  doAbortCapture,
  isUSBPermissionGranted,
  getCurrentDeviceName,
  hasUSBOTG,
};
