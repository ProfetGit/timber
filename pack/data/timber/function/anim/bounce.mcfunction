execute if score @s timber.t matches 1 run function timber:anim/fx/impact
execute if score @s timber.t matches 9 run function timber:anim/fx/thud {v:0.9,p:0.8}
execute if score @s timber.t matches 15 run function timber:anim/fx/thud {v:0.6,p:1.0}
execute if score @s timber.t matches 19.. run return run function timber:anim/phase {ph:3}
execute if score @s timber.t matches ..8 run function timber:anim/arc {t0:0,len:8,num:400,den:6400}
execute if score @s timber.t matches 9..14 run function timber:anim/arc {t0:8,len:6,num:160,den:3600}
execute if score @s timber.t matches 15..18 run function timber:anim/arc {t0:14,len:4,num:60,den:1600}
function timber:anim/apply
