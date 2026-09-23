execute if score #left timber.data matches ..0 run return 0
scoreboard players remove #left timber.data 1
scoreboard players set #roll timber.data 0
execute if score #unb timber.data matches 1.. store result score #roll timber.data run random value 0..999999
execute if score #unb timber.data matches 1.. run scoreboard players operation #roll timber.data %= #unb1 timber.data
execute if score #roll timber.data matches 0 run scoreboard players add #dmg timber.data 1
function timber:tool/roll
