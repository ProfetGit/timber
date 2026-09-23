scoreboard players set #best timber.data 10500
execute store result storage timber:op f.yaw double 0.01 run scoreboard players get #byaw timber.data
data modify storage timber:op f.pitch set value 105.0d
scoreboard players set #drop timber.data 16
scoreboard players operation #r timber.data = #h timber.data
function timber:fall/drop_scan with storage timber:op f
