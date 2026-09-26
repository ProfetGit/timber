execute if score @s timber.t matches 1 run function timber:anim/shape {gx:980,gy:1000,gz:1060,lx:1140,lz:90}
execute if score @s timber.t matches 2 run kill @e[type=block_display,tag=timber.lf,distance=..0.01]
execute if score @s timber.t matches 2 run function timber:anim/shape {gx:1010,gy:1000,gz:985,lx:1000,lz:1000}
execute if score @s timber.t matches 3 run function timber:anim/shape {gx:1000,gy:1000,gz:1000,lx:1000,lz:1000}
execute if score @s timber.t matches 7 run function timber:anim/fx/thud {v:0.8,p:0.9}
execute if score @s timber.t matches 7 run function timber:anim/shape {gx:1040,gy:1000,gz:900,lx:1000,lz:1000}
execute if score @s timber.t matches 8 run function timber:anim/shape {gx:990,gy:1000,gz:1030,lx:1000,lz:1000}
execute if score @s timber.t matches 8.. run return run function timber:anim/ripple_start
function timber:anim/arc {t0:1,len:6,num:400,den:3600}
