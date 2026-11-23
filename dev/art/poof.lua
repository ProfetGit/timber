local RAMP = {
  {255,252,240}, {240,241,244}, {214,218,226}, {176,184,198}, {138,148,168},
}
local function hash(x,y,f) local h=(x*73856093 ~ y*19349663 ~ f*83492791) & 0xffff; return (h%100)/100 end
local function J(base, ph, amp, grow)
  local o={}
  for i,l in ipairs(base) do
    local k=ph+i*1.7
    o[i]={l[1]+amp*1.3*math.cos(k*1.3), l[2]+amp*math.sin(k), l[3]*grow*(1+amp*0.45*math.sin(k+1.1))}
  end
  return o
end
local P3 = {{4.5,9.5,3.4},{11.5,9.5,3.4},{8,5.2,3.9},{6.2,12,2.8},{10,12,2.8},{8,9,3.8}}
local P4 = {{3.6,9.5,3},{12.4,9,3},{5.8,5,3.3},{10.8,5.6,3.1},{8,12,3.2},{8,8.5,3.8}}
local BIG = {
 {{8,9.5,2.4}},
 {{6.4,10.2,2.3},{9.6,10.2,2.3}},
 {{8,9.5,3.9},{6,8,2.4}},
 {{5.5,9.5,3.4},{10.5,9.5,3.4},{8,6.5,3.6},{8,11,3.4}},
 P3, P4,
 J(P3,0.5,0.55,1.0), J(P4,2.2,0.55,1.0), J(P3,4.0,0.55,1.0),
 {{4,10,3.1},{12,9.5,3},{6,5.5,3},{10.5,5,2.8},{8,12.5,2.8},{8,8.5,3.2},{2,3.5,1.2}},
 {{4,10,2.9},{12,9.5,2.8},{6,5.5,2.8},{10.5,5,2.6},{8,12.5,2.6},{2,3.5,1.4},{14,4.5,1.3}},
 {{3.8,10,2.5},{12.2,9.5,2.4},{6,5,2.5},{10.5,4.8,2.3},{8,12.5,2.3},{1.8,3,1.5},{14.2,4,1.4},{2,13,1}},
 {{3.5,10,2.1},{12.5,9.5,2.1},{8,4.5,2.3},{8,12.5,2},{1.8,3,1.5},{14.2,3.6,1.3},{13.5,13.5,1.2}},
 {{3.2,10.5,1.7},{12.8,10,1.7},{8,4,1.8},{8,13,1.6},{1.6,2.5,1.3},{14.4,3,1.2},{14,14,1.2},{2,14,1}},
 {{3,8,1.6},{13,7,1.6},{8,3,1.5},{6.5,13,1.5},{12,12.5,1.2},{2,13,1}},
 {{2.5,7.5,1.1},{13.5,6.5,1.1},{8,2.2,1},{6,14,1},{12.5,13.5,0.9}},
}
local S4 = {{2.4,5,1.7},{5.8,4.6,1.7},{4,3,1.9},{4,5.8,1.8}}
local SMALL = {
 {{4,5,1.2}},
 {{3.2,5.2,1.3},{4.8,5.2,1.3}},
 {{4,5,2}},
 {{2.8,5,1.8},{5.4,5,1.8},{4,3.4,1.9}},
 {{2.6,5,1.8},{5.6,5,1.8},{4,3.2,2},{4,5.6,2}},
 S4,
 J(S4,0.6,0.35,1.0), J(S4,2.4,0.35,1.0), J(S4,4.2,0.35,0.95),
 {{2.5,5.2,1.6},{5.6,4.8,1.5},{3.8,3,1.5},{1,1.5,0.7}},
 {{2.5,5.2,1.5},{5.6,4.8,1.4},{3.8,3,1.4},{1,1.5,0.8}},
 {{2.4,5.4,1.3},{5.6,5,1.3},{4,2.9,1.3},{1,1.5,0.8}},
 {{2.4,5.5,1.1},{5.6,5,1.1},{4,2.8,1.2},{7,2,0.7}},
 {{2.2,5.5,0.9},{5.8,4.8,0.9},{4,2.4,1},{7,1.5,0.7}},
 {{1.5,4,0.8},{6.5,3.5,0.8},{4,1.6,0.8},{3.5,6.5,0.8}},
 {{1.2,3.5,0.7},{6.8,3,0.7},{4,1.2,0.6}},
}
local function render(img, lobes, N, f)
  local mask = {}
  local sh = {}
  for y=0,N-1 do for x=0,N-1 do
    local px,py = x+0.5,y+0.5
    local best, bd = nil, 1e9
    for _,l in ipairs(lobes) do
      local dx,dy = px-l[1], py-l[2]
      local d = math.sqrt(dx*dx+dy*dy)/l[3]
      if d<=1 and d<bd then bd=d; best={dx/l[3],dy/l[3],l} end
    end
    if best then
      mask[y*N+x]=true
      local dirt = (best[1]+best[2])*0.5            -- -1 top-left .. +1 bottom-right
      local low = (py/N-0.45)*0.9                    -- underside of whole puff
      sh[y*N+x] = dirt*0.55 + low + bd*0.25
    end
  end end
  if f>=9 then
    local thr = ({[9]=0.12,[10]=0.22,[11]=0.28,[12]=0.34,[13]=0.3,[14]=0.0,[15]=0.0})[f]
    if N==8 then thr = thr*0.6 end
    local rm = {}
    for y=0,N-1 do for x=0,N-1 do
      if mask[y*N+x] then
        local edge=false
        for _,d in ipairs({{1,0},{-1,0},{0,1},{0,-1}}) do
          local nx,ny=x+d[1],y+d[2]
          if nx<0 or ny<0 or nx>=N or ny>=N or not mask[ny*N+nx] then edge=true end
        end
        if edge and hash(x,y,f)<thr then rm[y*N+x]=true end
      end
    end end
    for k in pairs(rm) do mask[k]=nil end
  end
  for y=0,N-1 do for x=0,N-1 do
    if mask[y*N+x] then
      local s = sh[y*N+x]
      local i
      if s < -0.3 then i=1 elseif s < 0.0 then i=2 elseif s < 0.35 then i=3 elseif s < 0.75 then i=4 else i=5 end
      if f>=12 and i<3 then i=i+1 end
      local c=RAMP[i]
      img:putPixel(x,y,app.pixelColor.rgba(c[1],c[2],c[3],255))
    end
  end end
end
local function build(name, N, lobes)
  local spr = Sprite(N,N,ColorMode.RGB)
  spr.filename = name..".aseprite"
  for f=0,15 do
    if f>0 then spr:newEmptyFrame(f+1) end
    local img = Image(N,N,ColorMode.RGB)
    render(img, lobes[f+1], N, f)
    spr:newCel(spr.layers[1], f+1, img, Point(0,0))
    spr.frames[f+1].duration = 0.04
  end
  spr:saveAs(spr.filename)
  for f=0,15 do
    local im = Image(N,N,ColorMode.RGB); im:drawSprite(spr, f+1)
    im:saveAs(string.format("%s_%d.png", name, f))
  end
end
build(OUT.."poof", 16, BIG)
build(OUT.."poof_small", 8, SMALL)
