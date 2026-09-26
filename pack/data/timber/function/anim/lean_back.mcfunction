execute if score #l timber.data matches ..0 run return 0
execute if score #l timber.data matches 1 run function timber:anim/fx/creak
execute if score #l timber.data matches 1 run scoreboard players set @s timber.p -55
execute if score #l timber.data matches 2 run scoreboard players set @s timber.p -198
execute if score #l timber.data matches 3 run scoreboard players set @s timber.p -394
execute if score #l timber.data matches 4 run scoreboard players set @s timber.p -606
execute if score #l timber.data matches 5 run scoreboard players set @s timber.p -802
execute if score #l timber.data matches 6 run scoreboard players set @s timber.p -945
execute if score #l timber.data matches 7 run scoreboard players set @s timber.p -1000
execute if score #l timber.data matches 7 run function timber:anim/fx/crack
execute if score #l timber.data matches 7.. run function timber:anim/phase {ph:1}
