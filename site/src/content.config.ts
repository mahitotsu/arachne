import { defineCollection, z } from 'astro:content';

const essays = defineCollection({
  type: 'content',
  schema: z.object({
    title: z.string(),
    summary: z.string(),
    publishDate: z.coerce.date(),
    order: z.number().default(0),
    tags: z.array(z.string()).default([]),
  }),
});

const observations = defineCollection({
  type: 'content',
  schema: z.object({
    title: z.string(),
    principle: z.string(),
    fl: z.string(),
    hypothesis: z.string(),
    order: z.number().default(0),
  }),
});

export const collections = {
  essays,
  observations,
};