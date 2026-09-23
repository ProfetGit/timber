scoreboard players add #bt timber.data 1
execute if score #bt timber.data matches 4.. run scoreboard players set #bt timber.data 0
execute if score #bt timber.data matches 0 run return run data modify storage timber:op d.Tags[1] set value "timber.b0"
execute if score #bt timber.data matches 1 run return run data modify storage timber:op d.Tags[1] set value "timber.b1"
execute if score #bt timber.data matches 2 run return run data modify storage timber:op d.Tags[1] set value "timber.b2"
data modify storage timber:op d.Tags[1] set value "timber.b3"
