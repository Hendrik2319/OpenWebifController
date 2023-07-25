### Timers
* in StationSwitch
	* [x] configure single row selection
	* [x] add view of detail for selected timer
* in OpenWebifController
	* [x] add commands to context menu: delete, toggle, [ ] update all
	* [x] rename Timers to TimerData
* both
	* TimerTables
		* [-] merge TimerTables of both into one class -> nope
			* separate classes can be more specific and smaller
		* [x] add functions
			* "clean up timers"
		* [x] update row view after toggle or delete without a full reload of timer data
	* EPG dialog
		* [x] add "jump to time"