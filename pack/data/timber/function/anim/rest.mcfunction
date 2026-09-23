execute if score @s timber.t matches 1 run function timber:anim/fx/thud {v:0.4,p:1.25}
execute if score @s timber.t matches 1 run scoreboard players operation @s timber.p = @s timber.hit
execute if score @s timber.t matches 1 run scoreboard players operation @s timber.p -= @s timber.e
execute if score @s timber.t matches 1 run function timber:anim/apply
execute if score @s timber.t matches 13.. run function timber:anim/poof
