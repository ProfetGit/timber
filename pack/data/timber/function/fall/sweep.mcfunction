execute if score #th timber.data matches 13501.. run return run scoreboard players set #th timber.data 13500
scoreboard players set #hit timber.data 0
execute store result storage timber:op f.pitch double 0.01 run scoreboard players get #th timber.data
function timber:fall/scan with storage timber:op f
execute if score #hit timber.data matches 1 run return run scoreboard players remove #th timber.data 300
scoreboard players add #th timber.data 300
function timber:fall/sweep
