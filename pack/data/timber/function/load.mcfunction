data modify storage timber:meta version set value "1.1.0"
data modify storage timber:meta version_id set value 10100
data remove storage timber:meta requires
function timber:load/objectives
function timber:load/types
function timber:compat/extra_load
scoreboard players set #-1 timber.data -1
scoreboard players set #2 timber.data 2
scoreboard players set #8 timber.data 8
scoreboard players set #9 timber.data 9
scoreboard players set #10 timber.data 10
scoreboard players set #12 timber.data 12
scoreboard players set #16 timber.data 16
scoreboard players set #100 timber.data 100
scoreboard players set #380 timber.data 380
scoreboard players set #500 timber.data 500
scoreboard players set #1000 timber.data 1000
scoreboard players set #2250 timber.data 2250
scoreboard players set #300 timber.data 300
scoreboard players set #1500 timber.data 1500
scoreboard players set #9000 timber.data 9000
scoreboard players set #9250 timber.data 9250
scoreboard players set #10000 timber.data 10000
scoreboard players set #36000 timber.data 36000
execute unless score #stage timber.data = #stage timber.data run scoreboard players set #stage timber.data 0
function timber:config/defaults
function timber:load/curve
function #timber:api/loaded
