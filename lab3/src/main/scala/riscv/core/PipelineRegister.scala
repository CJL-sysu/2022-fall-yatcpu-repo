// Copyright 2021 Howard Lau
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package riscv.core

import chisel3._
import riscv.Parameters

class PipelineRegister(width: Int = Parameters.DataBits, defaultValue: UInt = 0.U) extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })
  // Lab3(PipelineRegister)
  val myreg = RegInit(UInt(width.W),defaultValue)
  val out =RegInit(UInt(width.W),defaultValue)
  when(io.flush){
    out := defaultValue
    myreg := defaultValue
  }
    .elsewhen(io.stall){
      out := myreg
    }
    .otherwise{
      myreg := io.in
      out := io.in
    }
  io.out := out  //在最后一步才给io.out赋值，是为了防止出现组合逻辑环路导致sbt "testOnly riscv.ThreeStageCPUTest"无法通过（sbt "testOnly riscv.PipelineRegisterTest"可以通过）
  //踩了很多次坑猜测出来的，可能是因为如果在前面的条件判断中就给io.out赋值，硬件就不会理会后面代码对io.out的再次赋值
  // Lab3(PipelineRegister) End
}
