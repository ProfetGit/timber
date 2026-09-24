fillbiome -30 -64 -30 60 0 60 minecraft:forest
execute positioned -2 -60 1 run function tscene:tree/fancy_b
execute positioned 3 -60 3 run function tscene:tree/birch_a
execute positioned 2 -60 13 run function tscene:tree/oak_e
execute positioned -3 -60 8 run function tscene:tree/birch_c
execute positioned -4 -60 15 run function tscene:tree/oak_a
execute positioned -9 -60 4 run function tscene:tree/oak_c
execute positioned -9 -60 12 run function tscene:tree/birch_b
execute positioned 8 -60 8 run function tscene:tree/oak_c
fill 12 -60 -2 30 -60 20 minecraft:short_grass replace air
time set 12650
weather clear
kill @e[type=item]
data merge storage tscene:cfg {t:"oak",cx:8,cy:-60,cz:8,fx:5.5,fy:-60,fz:8.5,fyaw:-90}
data modify storage tscene:cfg fp set value {x:5.5,y:-60,z:8.5,yaw:-90,pitch:20}
data modify storage tscene:cfg side set value {x:10.5,y:-59,z:21.5,yaw:180,pitch:-8}
