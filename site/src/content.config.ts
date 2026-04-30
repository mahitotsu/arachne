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

export const collections = {
  essays,
};