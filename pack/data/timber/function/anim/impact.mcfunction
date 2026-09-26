scoreboard players operation @s timber.p = @s timber.hit
function timber:anim/phase {ph:2}
tag @s remove timber.flex
scoreboard players set @s timber.bn 0
scoreboard players set @s timber.bv 0
function timber:anim/shape {gx:1060,gy:1000,gz:820,lx:1080,lz:380}
function timber:anim/fx/impact
