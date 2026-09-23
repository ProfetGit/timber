execute if score @s timber.t matches 5 run function timber:anim/fx/creak
execute if score @s timber.t matches 5..8 run function timber:anim/smooth
execute if score @s timber.t matches 9.. run return run function timber:anim/phase {ph:1}
scoreboard players set @s timber.p -250
execute if score @s timber.t matches 1 run scoreboard players set @s timber.p -109
execute if score @s timber.t matches 2 run scoreboard players set @s timber.p -188
execute if score @s timber.t matches 3 run scoreboard players set @s timber.p -234
execute if score @s timber.t matches ..4 run function timber:anim/apply
