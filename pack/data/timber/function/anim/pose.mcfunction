# the trunk's angular velocity and acceleration this tick (centi-degrees per tick), for the bend's spring
scoreboard players operation #w timber.data = @s timber.p
scoreboard players operation #w timber.data -= @s timber.pv
scoreboard players operation #acc timber.data = #w timber.data
scoreboard players operation #acc timber.data -= @s timber.wv
scoreboard players operation @s timber.pv = @s timber.p
scoreboard players operation @s timber.wv = #w timber.data
execute if entity @s[tag=timber.shiver] run function timber:anim/shiver
execute if entity @s[tag=timber.flex,tag=!timber.shiver] run function timber:anim/spring
# the client receives entity pitch as a byte (1.40625 degree steps), so the entity takes the largest step not above
# the pitch (at most 90 degrees) and each display's transformation carries the rest, exactly, as a float
scoreboard players operation #p64 timber.data = @s timber.p
scoreboard players operation #p64 timber.data *= #16 timber.data
scoreboard players operation #p64 timber.data /= #25 timber.data
scoreboard players operation #qs timber.data = #p64 timber.data
scoreboard players operation #qs timber.data /= #90 timber.data
execute if score #qs timber.data matches 65.. run scoreboard players set #qs timber.data 64
execute if score #qs timber.data matches ..-65 run scoreboard players set #qs timber.data -64
scoreboard players operation #x64 timber.data = #qs timber.data
scoreboard players operation #x64 timber.data *= #-90 timber.data
scoreboard players operation #x64 timber.data += #p64 timber.data
execute unless score @s timber.qs = #qs timber.data run function timber:anim/move
execute if score @s timber.ph matches 5 run function timber:anim/move
execute unless score @s timber.xl = #x64 timber.data run tag @s add timber.xf
execute unless score @s timber.bl = @s timber.bn run tag @s add timber.xf
execute if entity @s[tag=timber.xf] run function timber:anim/xf
