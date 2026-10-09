# a tree with open air under its cut falls straight down onto the ground before it leans and tips. The controller rides
# with the displays (they all stand on it), so this runs last in the tick, after the pose has found them where they were
execute if score @s timber.t matches ..2 run return 0
scoreboard players operation #tau timber.data = @s timber.t
scoreboard players remove #tau timber.data 3
scoreboard players operation #tt timber.data = @s timber.ft
scoreboard players operation #tt timber.data *= @s timber.ft
scoreboard players operation #a timber.data = #tau timber.data
scoreboard players operation #a timber.data *= #tau timber.data
scoreboard players operation #q timber.data = #tt timber.data
scoreboard players operation #q timber.data -= #a timber.data
scoreboard players operation #u timber.data = @s timber.fd
scoreboard players remove #u timber.data 300
scoreboard players operation #u timber.data *= #q timber.data
scoreboard players operation #u timber.data /= #tt timber.data
scoreboard players operation #y timber.data = @s timber.y0
scoreboard players operation #y timber.data -= @s timber.fd
scoreboard players operation #y timber.data += #u timber.data
execute store result entity @s Pos[1] double 0.001 run scoreboard players get #y timber.data
tp @e[type=block_display,tag=timber.d,distance=..0.01] @s
execute if score #tau timber.data < @s timber.ft run return 0
execute at @s run function timber:anim/fx/land
function timber:anim/phase {ph:0}
scoreboard players set @s timber.t 4
