execute if score #l timber.data matches ..0 run return 0
execute if score #l timber.data matches 1 run function timber:anim/fx/creak
execute if score #l timber.data matches 1 run scoreboard players set @s timber.p -150
execute if score #l timber.data matches 2 run scoreboard players set @s timber.p -340
execute if score #l timber.data matches 3 run scoreboard players set @s timber.p -510
execute if score #l timber.data matches 4 run scoreboard players set @s timber.p -640
execute if score #l timber.data matches 5 run scoreboard players set @s timber.p -730
execute if score #l timber.data matches 6 run scoreboard players set @s timber.p -780
execute if score #l timber.data matches 7 run scoreboard players set @s timber.p -800
execute if score #l timber.data matches 1..7 run function timber:anim/apply
execute if score #l timber.data matches 9.. run function timber:anim/phase {ph:1}
