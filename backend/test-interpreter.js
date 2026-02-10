/**
 * Test script — check what the Interactions API actually returns for code_execution
 */

import { GoogleGenAI } from '@google/genai';
import dotenv from 'dotenv';
dotenv.config();

const API_KEY = process.env.GEMINI_API_KEY || '';

if (!API_KEY) {
  console.error('No API key found. Set GEMINI_API_KEY or check src/config.js');
  process.exit(1);
}

const client = new GoogleGenAI({ apiKey: API_KEY });

console.log('Creating interaction with code_execution tool...');

const interaction = await client.interactions.create({
  model: 'gemini-3-flash-preview',
  input: 'Write a simple Python script that prints "Hello World" and calculates 2+2.  and also generate a plot for matplotlib for simple linear regression. Print both results.',
  system_instruction: 'You are a Python code execution assistant. Always use the code_execution tool.',
  tools: [{ type: 'code_execution' }],
  generation_config: {
    temperature: 0.7,
    max_output_tokens: 65536,
  },
});

console.log('\n=== RAW INTERACTION ===');
console.log(JSON.stringify(interaction, null, 2));

console.log('\n=== OUTPUTS ===');
if (interaction.outputs) {
  for (let i = 0; i < interaction.outputs.length; i++) {
    const out = interaction.outputs[i];
    console.log(`\n--- Output ${i} ---`);
    console.log('Type:', out.type);
    console.log('Keys:', Object.keys(out));
    console.log('Full:', JSON.stringify(out, null, 2));
  }
} else {
  console.log('No outputs field!');
  console.log('Keys on interaction:', Object.keys(interaction));
}

// Also check other possible response fields
console.log('\n=== OTHER FIELDS ===');
for (const key of Object.keys(interaction)) {
  if (key !== 'outputs') {
    const val = interaction[key];
    if (typeof val === 'object') {
      console.log(`${key}:`, JSON.stringify(val, null, 2).substring(0, 500));
    } else {
      console.log(`${key}:`, val);
    }
  }
}
