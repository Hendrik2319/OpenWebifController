# OpenWebifController
`OpenWebifController` is a tool to work with the interface provided by the [OpenWebif](https://github.com/E2OpenPlugins/e2openplugin-OpenWebif) plugin for Enigma2-based set-top boxes (STBs). It's a replacement for the web interface provided by the plugin and uses its API to gain the displayed information.  

`OpenWebifController` includes a subtool named [`StationSwitch`]
(/src/net/schwarzbaer/java/tools/openwebifcontroller/StationSwitch.java), which is a small software based "remote control".

### Usage
You can download a release [here](https://github.com/Hendrik2319/OpenWebifController/releases).  
You will need a JAVA 17 VM.

### Development
`OpenWebifController` is configured as a Eclipse project.  
It depends on following libraries:
* [`JavaLib_OpenWebif`](https://github.com/Hendrik2319/JavaLib_OpenWebif)
* [`JavaLib_JSON_Parser`](https://github.com/Hendrik2319/JavaLib_JSON_Parser)
* [`JavaLib_Common_Essentials`](https://github.com/Hendrik2319/JavaLib_Common_Essentials)
* [`JavaLib_Common_Dialogs`](https://github.com/Hendrik2319/JavaLib_Common_Dialogs)
* [`ImageMapEditor`](https://github.com/Hendrik2319/ImageMapEditor)

These libraries are imported as "project imports" in Eclipse. 
If you want to develop for your own and
* you use Eclipse as IDE,
	* then you should clone the projects above too and add them to the same workspace as the `OpenWebifController` project.
* you use another IDE (e.q. VS Code)
	* then you should clone the said projects, build JAR files of them and add the JAR files as libraries.

### Screenshots
`OpenWebifController`  
Screenshot1: Bouquets & Stations  
<img alt="Screenshot1: Bouquets & Stations" title="Screenshot1: Bouquets & Stations" width="300" src="/github/Screenshot1 - Bouquets & Stations.png" />  
Screenshot2: Timers  
<img alt="Screenshot2: Timers"              title="Screenshot2: Timers"              width="300" src="/github/Screenshot2 - Timers.png" />  

`StationSwitch`
<img alt="Screenshot3: StationSwitch"       title="Screenshot3: StationSwitch"       width="300" src="/github/Screenshot3 - StationSwitch.png" />  
