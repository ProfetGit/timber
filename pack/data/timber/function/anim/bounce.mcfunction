execute if score @s timber.t matches 1 run function timber:anim/fx/impact
execute if score @s timber.t matches 7 run function timber:anim/fx/thud {v:0.8,p:0.9}
execute if score @s timber.t matches 8.. run return run function timber:anim/ripple_start
function timber:anim/arc {t0:0,len:7,num:400,den:4900}
function timber:anim/apply
