execute if score @s timber.t matches ..4 run function timber:anim/arc {t0:0,len:4,num:140,den:1600}
execute if score @s timber.t matches 4 run function timber:anim/fx/thud {v:0.5,p:1.2}
function timber:anim/ring_go
function timber:anim/pop_tick
function timber:anim/ring_fx_go
execute if score @s timber.kf > @s timber.km unless entity @e[type=block_display,tag=timber.pop,distance=..0.01,limit=1] run function timber:anim/finish
