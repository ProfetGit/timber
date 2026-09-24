data modify storage timber:anim q set value []
$data modify storage timber:anim q append from storage timber:anim s.drops[{y:$(ry)}].Item
$data remove storage timber:anim s.drops[{y:$(ry)}]
$execute rotated $(yaw) $(p) positioned ^ ^$(kk) ^$(lz) run function timber:anim/ring_pop with storage timber:anim s
