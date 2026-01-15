session:answer();
ans = "电话接通"
local count=0
while session:ready() == true do
  local caller = session:getVariable('caller_id_name')
  local callee = session:getVariable('destination_number')
  local uuid = session:getVariable('uuid')
  local tts_voice = 'zhitian_emo'
  session:execute('set', 'tts_engine=unimrcp')
  session:execute('set', 'tts_voice=' ..tts_voice)
  session:execute("play_and_detect_speech", "say:{caller="..caller..",callee="..callee..",uuid="..uuid.."}"..ans.." detect:unimrcp {start-input-timers=false,caller="..caller..",callee="..callee..",uuid="..uuid.."} alimrcp")
  local xml = session:getVariable('detect_speech_result')
  if xml ~= nil and xml:match("<result>(.-)</result>") ~= nil then
    freeswitch.consoleLog("NOTICE", caller.."识别结果:"..xml .."\n")
    ans = xml:match("<result>(.-)</result>")
    count=0
  else
    freeswitch.consoleLog("NOTICE", caller.."识别失败:"..xml)
    ans = '没有说话'
    count=count+1
    if(count>=3) then
      freeswitch.consoleLog("NOTICE", "超过等待次数："..count)
      session:execute("speak","unimrcp|"..tts_voice.."|{caller="..caller..",callee="..callee..",uuid="..uuid.."}长时间没说话，要再见了")
      session:hangup()
      break
    end
  end
end
