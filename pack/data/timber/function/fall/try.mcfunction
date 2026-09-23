scoreboard players operation #yaw timber.data = #pyaw timber.data
$scoreboard players add #yaw timber.data $(off)
scoreboard players operation #yaw timber.data %= #36000 timber.data
execute store result storage timber:op f.yaw double 0.01 run scoreboard players get #yaw timber.data
scoreboard players set #th timber.data 600
function timber:fall/sweep
execute if score #th timber.data > #best timber.data run scoreboard players operation #byaw timber.data = #yaw timber.data
execute if score #th timber.data > #best timber.data run scoreboard players operation #best timber.data = #th timber.data
execute if score #th timber.data matches 8500.. run scoreboard players set #done timber.data 1
